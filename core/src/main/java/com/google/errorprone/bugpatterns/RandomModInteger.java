/*
 * Copyright 2016 The Error Prone Authors.
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

import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.BinaryTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree.Kind;

/** @author Louis Wasserman */
@BugPattern(
    summary = "Use Random.nextInt(int).  Random.nextInt() % n can have negative results",
    severity = SeverityLevel.ERROR)
public class RandomModInteger extends BugChecker implements BinaryTreeMatcher {

  private static final Matcher<ExpressionTree> RANDOM_NEXT_INT =
      Matchers.instanceMethod()
          .onDescendantOf("java.util.Random")
          .named("nextInt")
          .withNoParameters();

  @Override
  public Description matchBinary(BinaryTree tree, VisitorState state) {
    if (tree.getKind() == Kind.REMAINDER
        && tree.getLeftOperand() instanceof MethodInvocationTree
        && RANDOM_NEXT_INT.matches(tree.getLeftOperand(), state)) {
      ExpressionTree randomExpr = ASTHelpers.getReceiver(tree.getLeftOperand());
      ExpressionTree modulus = tree.getRightOperand();
      return describeMatch(
          tree,
          SuggestedFix.replace(
              tree,
              String.format(
                  "%s.nextInt(%s)",
                  state.getSourceForNode(randomExpr), state.getSourceForNode(modulus))));
    }
    return Description.NO_MATCH;
  }
}
