/*
 * Copyright 2020 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.bugpatterns;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.hasAnnotation;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;

/** Matches {@code AutoValue_} uses outside the containing file. */
@BugPattern(
    severity = WARNING,
    summary =
        "Do not refer to the autogenerated AutoValue_ class outside the file containing the"
            + " corresponding @AutoValue base class.",
    explanation =
        "@AutoValue-annotated classes may form part of your API, but the AutoValue_ generated"
            + " classes should not. The fact that the generated classes are visible to other"
            + " classes within the same package is an implementation detail, and is best avoided."
            + " Ideally, any reference to the AutoValue_-prefixed class should be confined to a"
            + " single factory method, with other factories delegating to it if necessary.")
public final class AutoValueSubclassLeaked extends BugChecker
    implements CompilationUnitTreeMatcher {

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    ImmutableSet<Type> autoValueClassesFromThisFile = findAutoValueClasses(tree, state);
    scanAndReportAutoValueReferences(tree, autoValueClassesFromThisFile, state);
    return NO_MATCH;
  }

  private void scanAndReportAutoValueReferences(
      CompilationUnitTree tree,
      ImmutableSet<Type> autoValueClassesFromThisFile,
      VisitorState state) {
    new SuppressibleTreePathScanner<Void, Void>(state) {

      @Override
      public Void visitClass(ClassTree classTree, Void unused) {
        if (!ASTHelpers.getGeneratedBy(getSymbol(classTree), state).isEmpty()) {
          return null;
        }
        return super.visitClass(classTree, null);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree memberSelectTree, Void unused) {
        handle(memberSelectTree);
        return super.visitMemberSelect(memberSelectTree, null);
      }

      @Override
      public Void visitIdentifier(IdentifierTree identifierTree, Void unused) {
        handle(identifierTree);
        return super.visitIdentifier(identifierTree, null);
      }

      private void handle(Tree tree) {
        Symbol symbol = getSymbol(tree);
        if (symbol instanceof ClassSymbol
            && symbol.getSimpleName().toString().startsWith("AutoValue_")
            && autoValueClassesFromThisFile.stream()
                .noneMatch(av -> isSubtype(symbol.type, av, state))) {
          state.reportMatch(describeMatch(tree));
        }
      }
    }.scan(tree, null);
  }

  private static ImmutableSet<Type> findAutoValueClasses(
      CompilationUnitTree tree, VisitorState state) {
    ImmutableSet.Builder<Type> types = ImmutableSet.builder();
    tree.accept(
        new TreeScanner<Void, Void>() {
          @Override
          public Void visitClass(ClassTree classTree, Void unused) {
            if (hasAnnotation(classTree, AutoValue.class, state)) {
              types.add(getType(classTree));
            }
            return super.visitClass(classTree, null);
          }
        },
        null);
    return types.build();
  }
}
