/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     slacoin, jcarsique
 *
 */
package org.nuxeo.runtime.test.runner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.log4j.MDC;
import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Define execution rules for an annotated random bug.
 * <p>
 * Principle is to increase consistency on tests which have a random behavior.
 * Such test is a headache because:
 * <ul>
 * <li>some developers may ask to ignore a random test since it's not reliable
 * and produces useless noise most of the time,</li>
 * <li>however, the test may still be useful in continuous integration for
 * checking the non-random part of code it covers,</li>
 * <li>and, after all, there's a random bug which should be fixed!</li>
 * </ul>
 *
 * Compared to the @{@link Ignore} JUnit annotation, the advantage is to provide
 * different behaviors for different use cases. The wanted behavior depending on
 * whereas:
 * <ul>
 * <li>we are working on something else and don't want being bothered by an
 * unreliable test,</li>
 * <li>we are working on the covered code and want to be warned in case of
 * regression,</li>
 * <li>we are working on the random bug and want to reproduce it.</li>
 * </ul>
 * That means that a random bug cannot be removed, but must be ignorable or
 * executable with attempts to reproduce or hide its random aspect, depending on
 * its execution context.
 *
 * </p>
 * <p>
 * For instance: <blockquote>
 *
 * <pre>
 * <code>
 * import org.nuxeo.runtime.test.runner.FeaturesRunner;
 * import org.nuxeo.runtime.test.RandomBugRule;
 *
 * {@literal @}RunWith(FeaturesRunner.class)
 * public class TestSample {
 *     public static final String NXP99999 = "Some comment or description";
 *
 *     {@literal @}Test
 *     {@literal @}RandomBugRule.Repeat(issue = NXP99999, onFailure=5, onSuccess=50)
 *     public void testWhichFailsSometimes() throws Exception {
 *         assertTrue(java.lang.Math.random() > 0.2);
 *     }
 * }
 * </code>
 * </pre>
 *
 * </blockquote> In the above example, the test fails sometimes.<br>
 * With the {@code @RandomBugRule.Repeat} annotation, it will be repeated in
 * case of failure up to 5 times until success. This is the default
 * {@link MODE#RELAX} mode.<br>
 * In order to reproduce the bug, use the {@link MODE#STRICT} mode. It will be
 * repeated in case of success up to 50 times until failure.<br>
 * In {@link MODE#BYPASS} mode, the test is ignored.
 * </p>
 *
 * @see MODE
 * @see MODE_PROPERTY
 * @since 5.9.5
 */
public class RandomBug implements MethodRule {

    public static final String MODE_PROPERTY = "nuxeo.tests.random.mode";

    /**
     * <ul>
     * <li>BYPASS: the test is ignored. Like with @{@link Ignore} JUnit
     * annotation.</li>
     * <li>STRICT: the test must fail. On success, the test is repeated until
     * failure or the limit number of tries {@link Repeat#onSuccess()} is
     * reached. If it does not fail during the tries, then the whole test class
     * is marked as failed.</li>
     * <li>RELAX: the test must succeed. On failure, the test is repeated until
     * success or the limit number of tries {@link Repeat#onFailure()} is
     * reached.</li>
     * </ul>
     */
    public static enum MODE {
        BYPASS, STRICT, RELAX
    };

    /**
     * The default mode if {@link MODE_PROPERTY} is not set.
     */
    public static final MODE DEFAULT = MODE.RELAX;

    /**
     * Repeat condition based on
     *
     * @see MODE
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.TYPE })
    public @interface Repeat {
        /**
         * Reference in issue management system. Recommendation is to use a
         * constant which name is the issue reference and value is a description
         * or comment.
         */
        String issue();

        /**
         * Times to repeat until failure in case of success
         */
        int onSuccess() default 30;

        /**
         * Times to repeat until success in case of failure
         */
        int onFailure() default 10;
    }

    private static class RepeatOnFailure extends Statement {
        private final int times;

        private final Statement statement;

        private String issue;

        private RepeatOnFailure(Statement statement, int times, String issue) {
            this.statement = statement;
            this.times = times;
            this.issue = issue;
        }

        @Override
        public void evaluate() throws Throwable {
            Error error = new AssertionError(String.format(
                    "No success after %d tries. Either the bug is not random "
                            + "or you should increase the 'onFailure' value.\n"
                            + "Issue: %s", times, issue));
            for (int i = 1; i <= times; i++) {
                MDC.put("fRepeat", i);
                try {
                    statement.evaluate();
                    return;
                } catch (Throwable t) {
                    error.addSuppressed(t);
                } finally {
                    MDC.remove("fRepeat");
                }
            }
            throw error;
        }
    }

    private static class RepeatOnSuccess extends Statement {
        private final int times;

        private final Statement statement;

        private String issue;

        private RepeatOnSuccess(Statement statement, int times, String issue) {
            this.statement = statement;
            this.times = times;
            this.issue = issue;
        }

        @Override
        public void evaluate() throws Throwable {
            Error error = new AssertionError(String.format(
                    "No failure after %d tries. Either the bug is fixed "
                            + "or you should increase the 'onSuccess' value.\n"
                            + "Issue: %s", times, issue));
            for (int i = 1; i <= times; i++) {
                MDC.put("fRepeat", i);
                try {
                    statement.evaluate();
                } finally {
                    MDC.remove("fRepeat");
                }
            }
            throw error;
        }
    }

    private static class NoopStatement extends Statement {
        private String issue;

        public NoopStatement(String issue) {
            this.issue = issue;
        }

        @Override
        public void evaluate() throws Throwable {
            throw new AssumptionViolatedException(
                    "Random bug ignored (bypass mode): " + issue);
        }
    }

    @Override
    public Statement apply(Statement statement, FrameworkMethod method,
            Object target) {
        Repeat repeat = method.getAnnotation(Repeat.class);
        if (repeat == null) {
            repeat = target.getClass().getAnnotation(Repeat.class);
        }
        if (repeat == null) {
            return statement;
        }
        switch (getMode()) {
        case BYPASS:
            return new NoopStatement(repeat.issue());
        case STRICT:
            return new RepeatOnSuccess(statement, repeat.onSuccess(),
                    repeat.issue());
        case RELAX:
            return new RepeatOnFailure(statement, repeat.onFailure(),
                    repeat.issue());
        default:
            return statement;
        }

    }

    public static MODE getMode() {
        String mode = System.getProperty(MODE_PROPERTY, DEFAULT.name());
        return MODE.valueOf(mode.toUpperCase());
    }

}
