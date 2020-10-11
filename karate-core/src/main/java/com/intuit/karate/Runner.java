/*
 * The MIT License
 *
 * Copyright 2018 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate;

import com.intuit.karate.core.*;
import com.intuit.karate.job.JobConfig;
import com.intuit.karate.job.JobServer;
import com.intuit.karate.job.ScenarioJobServer;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 *
 * @author pthomas3
 */
public class Runner {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Runner.class);

    public static class Builder {

        Class optionsClass;
        int threadCount;
        int timeoutMinutes;
        String reportDir;
        String scenarioName;
        List<String> tags = new ArrayList();
        List<String> paths = new ArrayList();
        List<Resource> resources;
        Collection<ExecutionHook> hooks;
        ExecutionHookFactory hookFactory;
        JobConfig jobConfig;
        String env;

        String tagSelector() {
            return Tags.fromKarateOptionsTags(tags);
        }

        List<Resource> resolveResources() {
            RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(paths, tags, env, optionsClass);
            paths = options.features;
            tags = options.tags;
            if (scenarioName == null) { // this could have been set by cli (e.g. intellij) so preserve if needed
                scenarioName = options.name;
            }
            if (resources == null) {
                return FileUtils.scanForFeatureFiles(paths, Thread.currentThread().getContextClassLoader());
            }
            return resources;
        }

        String resolveReportDir() {
            if (reportDir == null) {
                reportDir = FileUtils.getBuildDir() + File.separator + ScriptBindings.SUREFIRE_REPORTS;
            }
            new File(reportDir).mkdirs();
            return reportDir;
        }

        JobServer jobServer() {
            return jobConfig == null ? null : new ScenarioJobServer(jobConfig, reportDir);
        }

        int resolveThreadCount() {
            if (threadCount < 1) {
                threadCount = 1;
            }
            return threadCount;
        }

        //======================================================================
        //

        public Builder env(String env) {
            this.env = env;
            return this;
        }

        public Builder path(String... paths) {
            this.paths.addAll(Arrays.asList(paths));
            return this;
        }

        public Builder path(List<String> paths) {
            if (paths != null) {
                this.paths.addAll(paths);
            }
            return this;
        }

        public Builder tags(List<String> tags) {
            if (tags != null) {
                this.tags.addAll(tags);
            }
            return this;
        }

        public Builder tags(String... tags) {
            this.tags.addAll(Arrays.asList(tags));
            return this;
        }

        public Builder resources(Collection<Resource> resources) {
            if (resources != null) {
                if (this.resources == null) {
                    this.resources = new ArrayList();
                }
                this.resources.addAll(resources);
            }
            return this;
        }

        public Builder resources(Resource... resources) {
            return resources(Arrays.asList(resources));
        }

        public Builder forClass(Class clazz) {
            this.optionsClass = clazz;
            return this;
        }

        public Builder reportDir(String dir) {
            this.reportDir = dir;
            return this;
        }

        public Builder scenarioName(String name) {
            this.scenarioName = name;
            return this;
        }

        public Builder timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return this;
        }

        public Builder hook(ExecutionHook hook) {
            if (hooks == null) {
                hooks = new ArrayList();
            }
            hooks.add(hook);
            return this;
        }

        public Builder hookFactory(ExecutionHookFactory hookFactory) {
            this.hookFactory = hookFactory;
            return this;
        }

        public Results parallel(int threadCount) {
            this.threadCount = threadCount;
            return Runner.parallel(this);
        }

        public Results startServerAndWait(JobConfig config) {
            this.jobConfig = config;
            this.threadCount = 1;
            return Runner.parallel(this);
        }

    }

    public static Builder forClass(Class clazz) {
        Builder builder = new Builder();
        return builder.forClass(clazz);
    }

    public static Builder path(String... paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }

    public static Builder path(List<String> paths) {
        Builder builder = new Builder();
        return builder.path(paths);
    }

    //==========================================================================
    //
    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        return parallel(clazz, threadCount, reportDir, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir, String env) {
        return new Builder().forClass(clazz).reportDir(reportDir).env(env).parallel(threadCount);
    }
    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, null, threadCount, reportDir, null);
    }

    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir, String env) {
        return parallel(tags, paths, null, null, threadCount, reportDir, env);
    }

    public static Results parallel(int threadCount, String... tagsOrPaths) {
        return parallel(null, threadCount, tagsOrPaths);
    }

    public static Results parallel(String reportDir, int threadCount, String... tagsOrPaths) {
        List<String> tags = new ArrayList();
        List<String> paths = new ArrayList();
        for (String s : tagsOrPaths) {
            s = StringUtils.trimToEmpty(s);
            if (s.startsWith("~") || s.startsWith("@")) {
                tags.add(s);
            } else {
                paths.add(s);
            }
        }
        return parallel(tags, paths, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, String scenarioName,
                                   List<ExecutionHook> hooks, int threadCount, String reportDir, String env) {
        Builder options = new Builder();
        options.tags = tags;
        options.paths = paths;
        options.scenarioName = scenarioName;
        options.hooks = hooks;
        options.reportDir = reportDir;
        options.env = env;
        return options.parallel(threadCount);
    }

    public static Results parallel(List<Resource> resources, int threadCount, String reportDir) {
        Builder options = new Builder();
        options.resources = resources;
        options.reportDir = reportDir;
        return options.parallel(threadCount);
    }

    private static void onFeatureDone(Results results, ExecutionContext execContext, String reportDir, int index, int count) {
        FeatureResult result = execContext.result;
        Feature feature = execContext.featureContext.feature;
        if (result.getScenarioCount() > 0) { // possible that zero scenarios matched tags
            try { // edge case that reports are not writable
                File file = Engine.saveResultJson(reportDir, result, null);
                if (result.getScenarioCount() < 500) {
                    // TODO this routine simply cannot handle that size
                    Engine.saveResultXml(reportDir, result, null);
                }
                String status = result.isFailed() ? "fail" : "pass";
                LOGGER.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                result.printStats(file.getPath());
            } catch (Exception e) {
                LOGGER.error("<<error>> unable to write report file(s): {}", e.getMessage());
                result.printStats(null);
            }
        } else {
            results.addToSkipCount(1);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
            }
        }
    }

    public static Results parallel(Builder options) {
        String reportDir = options.resolveReportDir();
        // order matters, server depends on reportDir resolution
        JobServer jobServer = options.jobServer();
        int threadCount = options.resolveThreadCount();
        Results results = Results.startTimer(threadCount);
        results.setReportDir(reportDir);
        if (options.hooks != null) {
            options.hooks.forEach(h -> h.beforeAll(results));
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ExecutorService featureExecutor = Executors.newWorkStealingPool(threadCount);
        List<CompletableFuture> futures = new ArrayList();
        CompletableFuture latch = new CompletableFuture();
        Subscriber<CompletableFuture> subscriber = new Subscriber<CompletableFuture>() {
            @Override
            public void onNext(CompletableFuture result) {
                futures.add(result);
            }

            @Override
            public void onComplete() {
                latch.complete(Boolean.TRUE);
            }
        };
        List<Resource> resources = options.resolveResources();
        int count = resources.size();
        List<FeatureResult> featureResults = new ArrayList(count);
        ParallelProcessor<Resource, CompletableFuture> processor = new ParallelProcessor<Resource, CompletableFuture>(featureExecutor, resources.iterator()) {
            int index = 0;

            @Override
            public Iterator<CompletableFuture> process(Resource resource) {
                CompletableFuture future = new CompletableFuture();
                try {
                    Feature feature = FeatureParser.parse(resource);
                    feature.setCallName(options.scenarioName);
                    feature.setCallLine(resource.getLine());
                    FeatureContext featureContext = new FeatureContext(options.env, feature, options.tagSelector());
                    CallContext callContext = CallContext.forAsync(feature, options.hooks, options.hookFactory, null, false);
                    ExecutionContext execContext = new ExecutionContext(results, results.getStartTime(), featureContext, callContext, reportDir,
                            r -> featureExecutor.submit(r), featureExecutor, classLoader);
                    featureResults.add(execContext.result);
                    if (jobServer != null) {
                        jobServer.addFeature(execContext, feature.getScenarioExecutionUnits(execContext), () -> {
                            onFeatureDone(results, execContext, reportDir, ++index, count);
                            future.complete(Boolean.TRUE);
                        });
                    } else {
                        FeatureExecutionUnit unit = new FeatureExecutionUnit(execContext);
                        unit.setNext(() -> {
                            onFeatureDone(results, execContext, reportDir, ++index, count);
                            future.complete(Boolean.TRUE);
                        });
                        unit.run();
                    }
                } catch (Exception e) {
                    future.complete(Boolean.FALSE);
                    LOGGER.error("runner failed: {}", e.getMessage());
                    results.setFailureReason(e);
                }
                return Collections.singletonList(future).iterator();
            }

        };
        try {
            if (jobServer != null) {
                jobServer.startExecutors();
            }
            processor.subscribe(subscriber);
            latch.join();
            CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[futures.size()]);
            CompletableFuture allFutures = CompletableFuture.allOf(futuresArray);
            LOGGER.info("waiting for {} parallel features to complete ...", futuresArray.length);
            if (options.timeoutMinutes > 0) {
                allFutures.get(options.timeoutMinutes, TimeUnit.MINUTES);
            } else {
                allFutures.join();
            }
            LOGGER.info("all features complete");
            results.stopTimer();
            featureExecutor.shutdownNow();
            HtmlSummaryReport summary = new HtmlSummaryReport();
            for (FeatureResult result : featureResults) {
                int scenarioCount = result.getScenarioCount();
                results.addToScenarioCount(scenarioCount);
                if (scenarioCount != 0) {
                    results.incrementFeatureCount();
                }
                results.addToFailCount(result.getFailedCount());
                results.addToTimeTaken(result.getDurationMillis());
                if (result.isFailed()) {
                    results.addToFailedList(result.getPackageQualifiedName(), result.getErrorMessages());
                }
                results.addScenarioResults(result.getScenarioResults());
                if (!result.isEmpty()) {
                    HtmlFeatureReport.saveFeatureResult(reportDir, result);
                    summary.addFeatureResult(result);
                }
            }
            // saving reports can in rare cases throw errors, so do within try block
            summary.save(reportDir);
            results.printStats(threadCount);
            Engine.saveStatsJson(reportDir, results);
            HtmlReport.saveTimeline(reportDir, results, null);
            if (options.hooks != null) {
                options.hooks.forEach(h -> h.afterAll(results));
            }
        } catch (Exception e) {
            LOGGER.error("runner failed: {}", e);
            results.setFailureReason(e);
        }
        return results;
    }

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        CallContext callContext = new CallContext(vars, evalKarateConfig);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, callContext);
        if (result.isFailed()) {
            throw result.getErrorsCombined();
        }
        return result.getResultAsPrimitiveMap();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(file, Thread.currentThread().getContextClassLoader());
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = FileUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(path);
        return runFeature(feature, vars, evalKarateConfig);
    }

    // this is called by karate-gatling !
    public static void callAsync(String path, List<String> tags, Map<String, Object> arg, ExecutionHook hook, Consumer<Runnable> system, Runnable next) {
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        String tagSelector = Tags.fromKarateOptionsTags(tags);
        FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
        CallContext callContext = CallContext.forAsync(feature, Collections.singletonList(hook), null, arg, true);
        ExecutionContext executionContext = new ExecutionContext(null, System.currentTimeMillis(), featureContext, callContext, null, system, null);
        FeatureExecutionUnit exec = new FeatureExecutionUnit(executionContext);
        exec.setNext(next);
        system.accept(exec);
    }

}
