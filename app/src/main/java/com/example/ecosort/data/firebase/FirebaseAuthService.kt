package com.example.ecosort.data.firebase

import android.content.Context
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.utils.SecurityManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthService @Inject constructor() {

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }

    // ==================== AUTHENTICATION ====================
    
    /**
     * Register a new user with Firebase Authentication and Firestore
Executing tasks: [:app:assembleDebug] in project C:\Users\benli\AndroidStudioProjects\EcoSort

Starting Gradle Daemon...
Gradle Daemon started in 2 s 291 ms

> Configure project :app
--W- Build Environment: AGConnect plugin version is: 1.9.1.301, Gradle version is: 8.13, Android Plugin version is: 8.0.2
--I- Using the AGConnect-Config file: C:\Users\benli\AndroidStudioProjects\EcoSort\app\agconnect-services.json
--W- The variant: debug, Use the json file: C:\Users\benli\AndroidStudioProjects\EcoSort\app\agconnect-services.json
--I- Using the AGConnect-Config file: C:\Users\benli\AndroidStudioProjects\EcoSort\app\agconnect-services.json
--W- The variant: release, Use the json file: C:\Users\benli\AndroidStudioProjects\EcoSort\app\agconnect-services.json
AGPBI: {"kind":"warning","text":"We recommend using a newer Android Gradle plugin to use compileSdk = 34\n\nThis Android Gradle plugin (8.0.2) was tested up to compileSdk = 33.\n\nYou are strongly encouraged to update your project to use a newer\nAndroid Gradle plugin that has been tested with compileSdk = 34.\n\nIf you are already using the latest version of the Android Gradle plugin,\nyou may need to wait until a newer version with support for compileSdk = 34 is available.\n\nTo suppress this warning, add/update\n    android.suppressUnsupportedCompileSdk=34\nto this project's gradle.properties.","sources":[{}]}

> Task :app:createDebugVariantModel UP-TO-DATE
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:dataBindingMergeDependencyArtifactsDebug UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:processDebugAGCPlugin UP-TO-DATE
> Task :app:processDebugGoogleServices UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:dataBindingGenBaseClassesDebug UP-TO-DATE
> Task :app:dataBindingTriggerDebug UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:processDebugJavaRes NO-SOURCE
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE
> Task :app:mergeLibDexDebug UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols UP-TO-DATE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:kaptGenerateStubsDebugKotlin
> Task :app:kaptDebugKotlin
> Task :app:compileDebugKotlin
e: file:///C:/Users/benli/AndroidStudioProjects/EcoSort/app/src/main/java/com/example/ecosort/ui/login/LoginViewModel.kt:370:48 Incompatible types: Result.Success<*> and Result<User>
e: file:///C:/Users/benli/AndroidStudioProjects/EcoSort/app/src/main/java/com/example/ecosort/ui/login/LoginViewModel.kt:370:48 One type argument expected. Use 'Success<*>' if you don't want to pass type arguments
e: file:///C:/Users/benli/AndroidStudioProjects/EcoSort/app/src/main/java/com/example/ecosort/utils/AuthMigrationHelper.kt:36:20 Unresolved reference: Success
e: file:///C:/Users/benli/AndroidStudioProjects/EcoSort/app/src/main/java/com/example/ecosort/utils/AuthMigrationHelper.kt:39:20 Unresolved reference: Error

> Task :app:compileDebugKotlin FAILED

[Incubating] Problems report is available at: file:///C:/Users/benli/AndroidStudioProjects/EcoSort/build/reports/problems/problems-report.html

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
   > Compilation error. See log for more details

* Try:
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.lambda$executeIfValid$1(ExecuteActionsTaskExecuter.java:130)
	at org.gradle.internal.Try$Failure.ifSuccessfulOrElse(Try.java:293)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:128)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:40)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
Caused by: org.gradle.workers.internal.DefaultWorkerExecutor$WorkExecutionException: A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
	at org.gradle.workers.internal.DefaultWorkerExecutor$WorkItemExecution.waitForCompletion(DefaultWorkerExecutor.java:287)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.lambda$waitForItemsAndGatherFailures$2(DefaultAsyncWorkTracker.java:130)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:335)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLocks(DefaultWorkerLeaseService.java:318)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withoutLock(DefaultWorkerLeaseService.java:323)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:126)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForItemsAndGatherFailures(DefaultAsyncWorkTracker.java:92)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForAll(DefaultAsyncWorkTracker.java:78)
	at org.gradle.internal.work.DefaultAsyncWorkTracker.waitForCompletion(DefaultAsyncWorkTracker.java:66)
	at org.gradle.api.internal.tasks.execution.TaskExecution$3.run(TaskExecution.java:252)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:30)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$1.execute(DefaultBuildOperationRunner.java:27)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.run(DefaultBuildOperationRunner.java:48)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeAction(TaskExecution.java:229)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeActions(TaskExecution.java:212)
	at org.gradle.api.internal.tasks.execution.TaskExecution.executeWithPreviousOutputFiles(TaskExecution.java:195)
	at org.gradle.api.internal.tasks.execution.TaskExecution.execute(TaskExecution.java:162)
	at org.gradle.internal.execution.steps.ExecuteStep.executeInternal(ExecuteStep.java:105)
	at org.gradle.internal.execution.steps.ExecuteStep.access$000(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:59)
	at org.gradle.internal.execution.steps.ExecuteStep$1.call(ExecuteStep.java:56)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:56)
	at org.gradle.internal.execution.steps.ExecuteStep.execute(ExecuteStep.java:44)
	at org.gradle.internal.execution.steps.CancelExecutionStep.execute(CancelExecutionStep.java:42)
	at org.gradle.internal.execution.steps.TimeoutStep.executeWithoutTimeout(TimeoutStep.java:75)
	at org.gradle.internal.execution.steps.TimeoutStep.execute(TimeoutStep.java:55)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:50)
	at org.gradle.internal.execution.steps.PreCreateOutputParentsStep.execute(PreCreateOutputParentsStep.java:28)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:67)
	at org.gradle.internal.execution.steps.RemovePreviousOutputsStep.execute(RemovePreviousOutputsStep.java:37)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:61)
	at org.gradle.internal.execution.steps.BroadcastChangingOutputsStep.execute(BroadcastChangingOutputsStep.java:26)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:69)
	at org.gradle.internal.execution.steps.CaptureOutputsAfterExecutionStep.execute(CaptureOutputsAfterExecutionStep.java:46)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:40)
	at org.gradle.internal.execution.steps.ResolveInputChangesStep.execute(ResolveInputChangesStep.java:29)
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:75)
	at org.gradle.internal.Either$Right.fold(Either.java:175)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:35)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:49)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:27)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:71)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:39)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:107)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:56)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:64)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:43)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:125)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:61)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:289)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:48)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:35)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:61)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:127)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:40)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:20)
	at org.jetbrains.kotlin.compilerRunner.GradleKotlinCompilerWork.run(GradleKotlinCompilerWork.kt:141)
	at org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction.execute(GradleCompilerRunnerWithWorkers.kt:73)
	at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)
	at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:174)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:194)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:127)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:169)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:132)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:164)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:133)
	... 2 more


Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.13/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 2m 24s
37 actionable tasks: 5 executed, 32 up-to-date
     * Uses Firebase Auth for password handling - no custom hashing needed
     */
    suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        userType: UserType,
        context: Context
    ): Result<User> {
        return try {
            // 1. Check username uniqueness BEFORE creating Firebase account
            val existingUsernameQuery = usersCollection.whereEqualTo("username", username).get().await()
            if (!existingUsernameQuery.isEmpty) {
                android.util.Log.w("FirebaseAuthService", "Username '$username' already exists")
                return Result.Error(Exception("Username already taken. Please choose a different username."))
            }
            
            // 2. Register with Firebase Authentication (handles password hashing securely)
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUid = authResult.user?.uid ?: throw Exception("Firebase UID not found")

            // 3. Create User object (no password hash needed - Firebase handles this)
            val user = User(
                id = 0, // Will be set by local DB, Firebase uses firebaseUid
                firebaseUid = firebaseUid,
                username = username,
                email = email,
                passwordHash = "", // No password hash needed - Firebase Auth handles this
                userType = userType
            )

            // 4. Save user profile to Firestore (no password hash stored)
            val userData = hashMapOf(
                "firebaseUid" to firebaseUid,
                "username" to username,
                "email" to email,
                "userType" to userType.name,
                "createdAt" to System.currentTimeMillis(),
                "itemsRecycled" to 0,
                "totalPoints" to 0,
                "profileImageUrl" to "",
                "bio" to "",
                "location" to "",
                "joinDate" to System.currentTimeMillis(),
                "lastActive" to System.currentTimeMillis(),
                "profileCompletion" to 0,
                "privacySettings" to "",
                "achievements" to "",
                "socialLinks" to "",
                "preferences" to ""
            )
            usersCollection.document(firebaseUid).set(userData).await()
            android.util.Log.d("FirebaseAuthService", "User registered and profile saved to Firestore: $username")

            Result.Success(user)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Firebase registration failed: ${e.message}", e)
            
            // Provide more specific error messages based on Firebase error codes
            val errorMessage = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true -> 
                    "Email address is already in use. Please use a different email."
                e.message?.contains("weak-password", ignoreCase = true) == true -> 
                    "Password is too weak. Please choose a stronger password with at least 6 characters."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> 
                    "Invalid email format. Please enter a valid email address."
                e.message?.contains("network", ignoreCase = true) == true -> 
                    "Network error. Please check your internet connection and try again."
                e.message?.contains("too-many-requests", ignoreCase = true) == true -> 
                    "Too many registration attempts. Please try again later."
                else -> 
                    "Registration failed: ${e.message ?: "Unknown error occurred"}"
            }
            
            Result.Error(Exception(errorMessage))
        }
    }

    /**
     * Update user profile in Firestore
     * Used for updating additional profile information after registration
     */
    suspend fun updateUserProfile(firebaseUid: String, userData: HashMap<String, Any>): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseAuthService", "Updating user profile: $firebaseUid")
            usersCollection.document(firebaseUid).update(userData).await()
            android.util.Log.d("FirebaseAuthService", "User profile updated successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to update user profile", e)
            Result.Error(e)
        }
    }

    /**
     * Create Firestore profile for existing Firebase Auth account (e.g., from social sign-in)
     * This is separate from updateUserProfile because .set() creates/replaces, while .update() requires existing doc
     */
    suspend fun createFirestoreProfile(firebaseUid: String, userData: HashMap<String, Any>): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseAuthService", "Creating Firestore profile: $firebaseUid")
            usersCollection.document(firebaseUid).set(userData).await()
            android.util.Log.d("FirebaseAuthService", "Firestore profile created successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to create Firestore profile", e)
            Result.Error(e)
        }
    }

    /**
     * Get user by email from Firestore
     * Used for checking if social user exists across devices
     */
    suspend fun getUserByEmail(email: String): User? {
        return try {
            android.util.Log.d("FirebaseAuthService", "🔍 Searching for user by email in Firestore: $email")
            val querySnapshot = usersCollection.whereEqualTo("email", email).get().await()
            
            if (querySnapshot.isEmpty) {
                android.util.Log.d("FirebaseAuthService", "❌ No user found with email: $email")
                return null
            }
            
            // 🔥 CRITICAL: Log number of results to catch duplicate emails
            if (querySnapshot.documents.size > 1) {
                android.util.Log.e("FirebaseAuthService", "⚠️ WARNING: Multiple users found with email $email! Count: ${querySnapshot.documents.size}")
                querySnapshot.documents.forEachIndexed { index, doc ->
                    android.util.Log.e("FirebaseAuthService", "  User $index: ${doc.id}, username: ${doc.getString("username")}, firebaseUid: ${doc.getString("firebaseUid")}")
                }
            }
            
            val document = querySnapshot.documents.first()
            val data = document.data ?: return null
            
            // 🔥 Log all retrieved data
            android.util.Log.d("FirebaseAuthService", "📄 Retrieved Firestore document:")
            android.util.Log.d("FirebaseAuthService", "  - Document ID: ${document.id}")
            android.util.Log.d("FirebaseAuthService", "  - Firebase UID: ${data["firebaseUid"]}")
            android.util.Log.d("FirebaseAuthService", "  - Username: ${data["username"]}")
            android.util.Log.d("FirebaseAuthService", "  - Email: ${data["email"]}")
            android.util.Log.d("FirebaseAuthService", "  - Social Account ID: ${data["socialAccountId"]}")
            android.util.Log.d("FirebaseAuthService", "  - Profile Image: ${data["profileImageUrl"]}")
            
            // 🔥 CRITICAL: Check if socialAccountId is missing (old accounts)
            val socialAccountId = data["socialAccountId"] as? String ?: ""
            if (socialAccountId.isEmpty()) {
                android.util.Log.w("FirebaseAuthService", "⚠️ WARNING: socialAccountId field is EMPTY or missing in Firestore!")
                android.util.Log.w("FirebaseAuthService", "  This is likely an old account. The field will be populated during login.")
            }
            
            // Convert Firestore document to User object
            val user = User(
                id = 0, // Will be assigned by local database
                firebaseUid = data["firebaseUid"] as? String ?: document.id,
                username = data["username"] as? String ?: "",
                email = data["email"] as? String ?: "",
                passwordHash = socialAccountId, // Social users store social ID in passwordHash - may be empty for old accounts
                userType = UserType.valueOf(data["userType"] as? String ?: "INDIVIDUAL"),
                profileImageUrl = data["profileImageUrl"] as? String,
                bio = data["bio"] as? String,
                location = data["location"] as? String,
                itemsRecycled = (data["itemsRecycled"] as? Long)?.toInt() ?: 0,
                totalPoints = (data["totalPoints"] as? Long)?.toInt() ?: 0,
                joinDate = data["joinDate"] as? Long ?: System.currentTimeMillis(),
                lastActive = data["lastActive"] as? Long ?: System.currentTimeMillis(),
                achievements = data["achievements"] as? String,
                socialLinks = data["socialLinks"] as? String,
                preferences = data["preferences"] as? String,
                privacySettings = data["privacySettings"] as? String,
                profileCompletion = (data["profileCompletion"] as? Long)?.toInt() ?: 0
            )
            
            android.util.Log.d("FirebaseAuthService", "✅ User converted to local User object:")
            android.util.Log.d("FirebaseAuthService", "  - Username: ${user.username}")
            android.util.Log.d("FirebaseAuthService", "  - Firebase UID: ${user.firebaseUid}")
            android.util.Log.d("FirebaseAuthService", "  - Password Hash (Social ID): ${user.passwordHash}")
            android.util.Log.d("FirebaseAuthService", "  - Profile Image: ${user.profileImageUrl}")
            
            user
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "💥 Error getting user by email", e)
            null
        }
    }

    /**
     * Sign in to Firebase Auth with email and password
     * Used for social users to establish Firebase Auth session for Firestore access
     */
    suspend fun signInWithEmailPassword(email: String, password: String) {
        try {
            android.util.Log.d("FirebaseAuthService", "🔐 Signing into Firebase Auth...")
            android.util.Log.d("FirebaseAuthService", "  - Email: $email")
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
            android.util.Log.d("FirebaseAuthService", "✅ Firebase Auth sign-in successful!")
            android.util.Log.d("FirebaseAuthService", "  - Current Firebase User: ${firebaseAuth.currentUser?.uid}")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "❌ Firebase Auth sign-in failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Update password for the currently signed-in Firebase user
     * Used to set a password for social sign-in accounts
     */
    suspend fun updateCurrentUserPassword(newPassword: String) {
        try {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                android.util.Log.d("FirebaseAuthService", "🔐 Updating password for Firebase user: ${currentUser.uid}")
                currentUser.updatePassword(newPassword).await()
                android.util.Log.d("FirebaseAuthService", "✅ Password updated successfully!")
            } else {
                android.util.Log.w("FirebaseAuthService", "⚠️ No current Firebase user to update password")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "❌ Failed to update password: ${e.message}", e)
            throw e
        }
    }

    /**
     * Authenticate user with Firebase Authentication
     * Simplified - relies entirely on Firebase Auth for password verification
     */
    suspend fun authenticateUser(usernameOrEmail: String, password: String, context: Context): Result<UserSession> {
        return try {
            // 1. Determine if input is email or username
            val isEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(usernameOrEmail).matches()
            
            val emailToUse = if (isEmail) {
                usernameOrEmail
            } else {
                // If it's a username, we need to find the corresponding email from Firestore
                val querySnapshot = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
                
                // Check for duplicate usernames
                if (querySnapshot.documents.size > 1) {
                    android.util.Log.w("FirebaseAuthService", "Multiple accounts found with username: $usernameOrEmail, selecting the first one")
                    // Instead of throwing an error, select the first document
                }
                
                val userDoc = querySnapshot.documents.firstOrNull()
                    ?: throw Exception("User not found with username: $usernameOrEmail")
                userDoc.data?.get("email") as? String 
                    ?: throw Exception("Email not found for username: $usernameOrEmail")
            }

            // 2. Authenticate with Firebase using the email
            val authResult = firebaseAuth.signInWithEmailAndPassword(emailToUse, password).await()
            val firebaseUid = authResult.user?.uid ?: throw Exception("Firebase UID not found")

            // 3. Get user profile from Firestore
            val userDoc = usersCollection.document(firebaseUid).get().await()
            val userData = userDoc.data ?: throw Exception("User profile not found in Firestore")

            val username = userData["username"] as? String ?: throw Exception("Username not found in Firestore")
            val email = userData["email"] as? String ?: throw Exception("Email not found in Firestore")
            val userType = UserType.valueOf(userData["userType"] as? String ?: "USER")

            // 4. Create UserSession (no custom password verification needed)
            val session = UserSession(
                userId = 0, // Will be set by local DB
                username = username,
                userType = userType,
                token = firebaseUid, // Using Firebase UID as session token for simplicity
                isLoggedIn = true
            )
            android.util.Log.d("FirebaseAuthService", "User authenticated via Firebase: $username (email: $email)")
            Result.Success(session)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Firebase authentication failed: ${e.message}", e)
            
            // Provide more specific error messages
            val errorMessage = when {
                e.message?.contains("Multiple accounts found", ignoreCase = true) == true -> 
                    e.message // Use the exact message we created
                e.message?.contains("User not found with username", ignoreCase = true) == true -> 
                    "No account found with this username. Please check your username or try using your email address."
                e.message?.contains("Email not found for username", ignoreCase = true) == true -> 
                    "Account data incomplete. Please contact support."
                e.message?.contains("user-not-found", ignoreCase = true) == true -> 
                    "No account found with this username/email."
                e.message?.contains("wrong-password", ignoreCase = true) == true -> 
                    "Incorrect password. Please try again."
                e.message?.contains("invalid-email", ignoreCase = true) == true -> 
                    "Invalid email format."
                e.message?.contains("user-disabled", ignoreCase = true) == true -> 
                    "This account has been disabled."
                e.message?.contains("too-many-requests", ignoreCase = true) == true -> 
                    "Too many failed login attempts. Please try again later."
                e.message?.contains("network", ignoreCase = true) == true -> 
                    "Network error. Please check your internet connection."
                else -> 
                    "Authentication failed: ${e.message ?: "Unknown error occurred"}"
            }
            
            Result.Error(Exception(errorMessage))
        }
    }

    /**
     * Migrate existing local user to Firebase Authentication
     * Note: This requires the user to re-enter their password for security
     */
    suspend fun migrateUserToFirebase(user: User, context: Context): Result<Unit> {
        return try {
            // Check if user already has a Firebase UID
            if (!user.firebaseUid.isNullOrBlank()) {
                android.util.Log.d("FirebaseAuthService", "User ${user.username} already has Firebase UID. Skipping migration.")
                return Result.Success(Unit)
            }

            // For migration, we need the user to re-enter their password
            // This is a security requirement - we cannot migrate without the plain password
            android.util.Log.w("FirebaseAuthService", "Migration requires user to re-enter password for security")
            throw Exception("Migration requires password re-entry for security")

        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to migrate user ${user.username} to Firebase: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Get user data from Firebase Firestore
     * Simplified - no password hash handling needed
     */
    suspend fun getUserFromFirebase(username: String, context: Context): Result<User?> {
        return try {
            val querySnapshot = usersCollection.whereEqualTo("username", username).get().await()
            val userDoc = querySnapshot.documents.firstOrNull()

            if (userDoc != null) {
                val userData = userDoc.data ?: return Result.Success(null)
                val firebaseUid = userData.get("firebaseUid") as? String ?: userDoc.id

                val user = User(
                    id = 0, // Local ID will be set by Room
                    firebaseUid = firebaseUid,
                    username = userData["username"] as? String ?: "",
                    email = userData["email"] as? String ?: "",
                    passwordHash = "", // No password hash needed - Firebase Auth handles this
                    userType = UserType.valueOf(userData["userType"] as? String ?: "USER"),
                    createdAt = (userData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    itemsRecycled = (userData["itemsRecycled"] as? Number)?.toInt() ?: 0,
                    totalPoints = (userData["totalPoints"] as? Number)?.toInt() ?: 0,
                    profileImageUrl = userData["profileImageUrl"] as? String,
                    bio = userData["bio"] as? String,
                    location = userData["location"] as? String,
                    joinDate = (userData["joinDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    lastActive = (userData["lastActive"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    profileCompletion = (userData["profileCompletion"] as? Number)?.toInt() ?: 0,
                    privacySettings = userData["privacySettings"] as? String,
                    achievements = userData["achievements"] as? String,
                    socialLinks = userData["socialLinks"] as? String,
                    preferences = userData["preferences"] as? String
                )
                Result.Success(user)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to get user from Firebase: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Check if user is currently authenticated with Firebase
     */
    fun isUserAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Get current Firebase user
     */
    fun getCurrentFirebaseUser() = firebaseAuth.currentUser

    /**
     * Sign out from Firebase
     */
    suspend fun signOut() {
        try {
            firebaseAuth.signOut()
            android.util.Log.d("FirebaseAuthService", "User signed out from Firebase")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to sign out from Firebase: ${e.message}", e)
        }
    }
}