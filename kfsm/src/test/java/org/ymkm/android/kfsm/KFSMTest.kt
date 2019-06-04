package org.ymkm.android.kfsm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

open class TestContext : FSMContext
open class DummyRunner(private val context: TestContext? = null) : FSMRunner<TestContext, Int> {

    override var started: Boolean = true

    override val currentContext: TestContext
        get() = context ?: TestContext()
    override val currentState: State
        get() = TODO("not implemented")
    override val fsm: FSM<TestContext, Int>
        get() = TODO("not implemented")

    override fun feed(input: Int): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun feedAsync(input: Int): Deferred<Boolean> {
        TODO("not implemented")
    }

    override fun feedAsync(inputChannel: ReceiveChannel<Int>): Deferred<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stop() {
        started = false
    }

    override fun availableTransitions(): List<Transition> {
        TODO("not implemented")
    }
}


internal class ParityCheck : FSMContext {
    var index = -1
    var hasEvenNumberOfZeros = false
}

internal val checkNumberParity = kfsm<ParityCheck, Pair<Int, Int>> {
    "S1" initialState {
        id = 1
        enterWithState { runner ->
            runner.currentContext.hasEvenNumberOfZeros = true
        }
        transitions {
            "Got 0" transition {
                to = 2
                condition { input, runner ->
                    if (input != null) {
                        runner.currentContext.index = input.first
                    }
                    input?.second == 0
                }
            }
            "Got 1" transition {
                to = 1
                condition { input, runner ->
                    if (input != null) {
                        runner.currentContext.index = input.first
                    }
                    input?.second == 1
                }
            }
        }
    }
    "S2" state {
        id = 2
        enterWithState { runner ->
            runner.currentContext.hasEvenNumberOfZeros = false
        }
        transitions {
            "Got 0" transition {
                to = 1
                condition { input, runner ->
                    if (input != null) {
                        runner.currentContext.index = input.first
                    }
                    input?.second == 0
                }
            }
            "Got 1" transition {
                to = 2
                condition { input, runner ->
                    if (input != null) {
                        runner.currentContext.index = input.first
                    }
                    input?.second == 1
                }
            }
        }
    }
}


@RunWith(RobolectricTestRunner::class)
class KFSMTest {

    init {
        KFSM.Debug.setDebug(true)
    }

    @Test
    fun testShouldKFSMWithoutAnyStateFail() {
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {}
        }
        assertThat(thrownError.message).isEqualTo("Cannot create a KFSM without any state")
    }

    @Test
    fun testShouldKFSMWithoutInitialStateFail() {
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Cannot create a KFSM without a start state")
        val thrownError2 = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" state {
                    id = 1
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Cannot create a KFSM without a start state")
    }

    @Test
    fun testShouldTwoInitialStateBlocksFail() {
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1 }
                initialState { id = 2 }
            }
        }
        assertThat(thrownError.message).isEqualTo("Only one initial state is allowed within a graph")
    }

    @Test
    fun testShouldInitialStateWithoutIdFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {}
            }
        }
        assertThat(thrownError.message).isEqualTo("State ID is required")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" initialState { }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State ID is required")
    }

    @Test
    fun testShouldStateWithoutIdFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {}
            }
        }
        assertThat(thrownError.message).isEqualTo("State ID is required")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" state { }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State ID is required")
    }

    @Test
    fun testShouldInitialStateWithTwoIdParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; id = 1 }
            }
        }
        assertThat(thrownError.message).isEqualTo("State ID already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" initialState { id = 1; id = 1 }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State ID already set")
    }

    @Test
    fun testShouldStateWithTwoIdParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state { id = 1; id = 1 }
            }
        }
        assertThat(thrownError.message).isEqualTo("State ID already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" state { id = 1; id = 1 }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State ID already set")
    }

    @Test
    fun testShouldInitialStateWithTwoLabelParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { label = "l"; label = "r" }
            }
        }
        assertThat(thrownError.message).isEqualTo("State label already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" initialState { label = "r" }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State label already set")
    }

    @Test
    fun testShouldStateWithTwoLabelParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state { label = "l"; label = "r" }
            }
        }
        assertThat(thrownError.message).isEqualTo("State label already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                "l" state { label = "r" }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State label already set")
    }

    @Test
    fun testShouldTwoStatesWithDuplicateIdFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1 }
                state { id = 2; label = "LABEL21" }
                state { id = 2; label = "LABEL22" }
            }
        }
        assertThat(thrownError.message).isEqualTo("State State(id=2, label=LABEL22, isSource=false, isSink=true) : Duplicate StateId(id=2) found in graph")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1 }
                "LABEL21" state { id = 2 }
                "LABEL22" state { id = 2 }
            }
        }
        assertThat(thrownError2.message).isEqualTo("State State(id=2, label=LABEL22, isSource=false, isSink=true) : Duplicate StateId(id=2) found in graph")
    }

    @Test
    fun testShouldInitialStateWithTwoEnterActionParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; enter { }; enter { } }
            }
        }
        assertThat(thrownError.message).isEqualTo("Enter state action already defined")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; enter { }; enterWithState { } }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Enter state action already defined")
        val thrownError3 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; enterWithState { }; enterWithState { } }
            }
        }
        assertThat(thrownError3.message).isEqualTo("Enter state action already defined")
    }

    @Test
    fun testShouldInitialStateWithTwoExitActionParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; exit { }; exit { } }
            }
        }
        assertThat(thrownError.message).isEqualTo("Exit state action already defined")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; exit { }; exitWithState { } }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Exit state action already defined")
        val thrownError3 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState { id = 1; exitWithState { }; exitWithState { } }
            }
        }
        assertThat(thrownError3.message).isEqualTo("Exit state action already defined")
    }

    @Test
    fun testFSMToStringWithSingleInitialState() {
        val kfsm = kfsm<TestContext, Int> {
            initialState { id = 1; label = "LABEL" }
        }
        assertThat(kfsm.toString()).isEqualTo("FSM With 1 states.\n\n-> State(id=1, label=LABEL, isSource=true, isSink=true)")
        val kfsm2 = kfsm<TestContext, Int> {
            initialState { id = 2 }
        }
        assertThat(kfsm2.toString()).isEqualTo("FSM With 1 states.\n\n-> State(id=2, label=-, isSource=true, isSink=true)")
        val kfsm3 = kfsm<TestContext, Int> {
            "LABEL" initialState { id = 1 }
        }
        assertThat(kfsm3.toString()).isEqualTo("FSM With 1 states.\n\n-> State(id=1, label=LABEL, isSource=true, isSink=true)")
    }

    @Test
    fun testFSMToStringWith2States() {
        val kfsm = kfsm<TestContext, Int> {
            initialState { id = 1; label = "LABEL" }
            state { id = 2; label = "LABEL2" }
        }
        assertThat(kfsm.toString()).isEqualTo("FSM With 2 states.\n\n-> State(id=1, label=LABEL, isSource=true, isSink=true)\n-> State(id=2, label=LABEL2, isSource=false, isSink=true)")
        val kfsm2 = kfsm<TestContext, Int> {
            initialState { id = 1 }
            state { id = 2 }
        }
        assertThat(kfsm2.toString()).isEqualTo("FSM With 2 states.\n\n-> State(id=1, label=-, isSource=true, isSink=true)\n-> State(id=2, label=-, isSource=false, isSink=true)")
        val kfsm3 = kfsm<TestContext, Int> {
            "LABEL" initialState { id = 1 }
            "LABEL2" state { id = 2 }
        }
        assertThat(kfsm3.toString()).isEqualTo("FSM With 2 states.\n\n-> State(id=1, label=LABEL, isSource=true, isSink=true)\n-> State(id=2, label=LABEL2, isSource=false, isSink=true)")
    }

    @Test
    fun testShouldStateEnterExitActionBeCalled() {
        val builder = KState.Builder<TestContext, Int>(false)
        builder.id = 1
        var enterCalled = false
        var exitCalled = false
        builder.enter {
            enterCalled = true
        }
        builder.exit {
            exitCalled = true
        }
        val state = builder.build()
        state.onEnter(DummyRunner())
        assertThat(enterCalled).isTrue()
        assertThat(exitCalled).isFalse()
        state.onExit(DummyRunner())
        assertThat(enterCalled).isTrue()
        assertThat(exitCalled).isTrue()
    }

    @Test
    fun testShouldStateEnterExitWithStateActionBeCalled() {

        class TestContextInner : TestContext() {
            var i = 0
        }

        val builder = KState.Builder<TestContext, Int>(false)
        builder.id = 1
        builder.enterWithState {
            (it.currentContext as TestContextInner).i = 1
        }
        builder.exitWithState {
            (it.currentContext as TestContextInner).i = 2
        }
        val state = builder.build()
        val context = TestContextInner()
        assertThat(context.i).isEqualTo(0)
        state.onEnter(DummyRunner(context))
        assertThat(context.i).isEqualTo(1)
        state.onExit(DummyRunner(context))
        assertThat(context.i).isEqualTo(2)
    }

    @Test
    fun testShouldTransitionWithoutTargetStateIdFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        transition { }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Target state ID is required")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        "l" transition { }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Target state ID is required")
    }

    @Test
    fun testShouldTransitionWithTwoTargetStateIdParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        transition { to = 1; to = 2 }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Target state ID already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        "l" transition { to = 1; to = 2 }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Target state ID already set")
    }

    @Test
    fun testShouldTransitionWithTwoLabelParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        transition { to = 1; label = "L"; label = "l" }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Transition label already set")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        "l" transition { to = 1; label = "L" }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Transition label already set")
    }

    @Test
    fun testShouldTransitionWithDuplicateTargetStateIdFail() {
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        transition { to = 1 }
                        transition { to = 1 }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Transition(targetState=StateId(id=1), label=-) targets state StateId(id=1) already defined by previous transition definition : Transition(targetState=StateId(id=1), label=-)")
        val thrownError2 = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        "l1" transition { to = 1 }
                        "l2" transition { to = 1 }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Transition(targetState=StateId(id=1), label=l2) targets state StateId(id=1) already defined by previous transition definition : Transition(targetState=StateId(id=1), label=l1)")
        val thrownError3 = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                state {
                    id = 1
                    transitions {
                        "l1" transition { to = 1 }
                        transition(1, "l2")
                    }
                }
            }
        }
        assertThat(thrownError3.message).isEqualTo("Transition(targetState=StateId(id=1), label=l2) targets state StateId(id=1) already defined by previous transition definition : Transition(targetState=StateId(id=1), label=l1)")
    }

    @Test
    fun testShouldTransitionWithUndefinedTargetStateIdFail() {
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition { to = 2 }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Transition(targetState=StateId(id=2), label=-) : Target state StateId(id=2) undefined in graph")
        val thrownError2 = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        "l" transition { to = 2 }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Transition(targetState=StateId(id=2), label=l) : Target state StateId(id=2) undefined in graph")
        val thrownError3 = assertThrows(
            IllegalStateException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition(2, "l")
                    }
                }
            }
        }
        assertThat(thrownError3.message).isEqualTo("Transition(targetState=StateId(id=2), label=l) : Target state StateId(id=2) undefined in graph")
    }

    @Test
    fun testShouldTransitionWithTwoActionParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition {
                            to = 1
                            action { }
                            action { }
                        }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Transition action already defined")
        val thrownError2 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition {
                            to = 1
                            action { }
                            actionWithState { _, _ -> }
                        }
                    }
                }
            }
        }
        assertThat(thrownError2.message).isEqualTo("Transition action already defined")
        val thrownError3 = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition {
                            to = 1
                            actionWithState { _, _ -> }
                            actionWithState { _, _ -> }
                        }
                    }
                }
            }
        }
        assertThat(thrownError3.message).isEqualTo("Transition action already defined")
    }

    @Test
    fun testShouldTransitionWithTwoConditionParametersFail() {
        val thrownError = assertThrows(
            IllegalArgumentException::class.java
        ) {
            kfsm<TestContext, Int> {
                initialState {
                    id = 1
                    transitions {
                        transition {
                            to = 1
                            condition { _, _ -> true }
                            condition { _, _ -> false }
                        }
                    }
                }
            }
        }
        assertThat(thrownError.message).isEqualTo("Transition predicate already defined")
    }

    @Test
    fun testShouldTransitionActionWithoutPredicateBeCalled() {
        var actionCalled = false
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 1
                        action {
                            actionCalled = true
                        }
                    }
                }
            }
        }

        assertThat(kfsm.start(TestContext()).feed(0)).isTrue()
        assertThat(actionCalled).isTrue()

        actionCalled = false
        val kfsm2 = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        label = "TRLabel"
                        to = 1
                        actionWithState { transition, _ ->
                            actionCalled = true
                            with(transition) {
                                assertThat(label).isEqualTo("TRLabel")
                                assertThat(toStateId).isEqualTo(StateId(1))
                            }
                        }
                    }
                }
            }
        }
        assertThat(kfsm2.start(TestContext()).feed(0)).isTrue()
        assertThat(actionCalled).isTrue()
    }

    @Test
    fun testShouldTransitionActionWithPredicateBeCalledIfConditionMatches() {
        var actionCalled = false
        var conditionCalled = false
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 2
                        condition { input, _ -> conditionCalled = true; input == 0 }
                    }
                }
            }
            state {
                // Since we disallow transitions to duplicate target state IDs from a given state, create two states
                id = 2
                transitions {
                    transition {
                        to = 1
                        action {
                            actionCalled = true
                        }
                        condition { input, _ -> conditionCalled = true; input == 1 }
                    }
                }
            }
        }
        runBlocking {
            val runner = kfsm.start(TestContext())
            assertThat(runner.feedAsync(0).await()).isTrue()
            assertThat(conditionCalled).isTrue()
            assertThat(actionCalled).isFalse()
            actionCalled = false
            conditionCalled = false
            assertThat(runner.feedAsync(1).await()).isTrue()
            assertThat(conditionCalled).isTrue()
            assertThat(actionCalled).isTrue()
        }
    }

    @Test
    fun shouldFeedSyncBeWaitingOnMainThreadWhenFedWithValue() {
        var conditionCalled = false
        var timeSpent = 0L
        val sleepTime = 200L
        val now = System.currentTimeMillis()
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 1
                        action {
                            // Simulate some processing
                            conditionCalled = true
                            Thread.sleep(sleepTime)
                            timeSpent = System.currentTimeMillis() - now
                        }
                    }
                }
            }
        }
        val runner = kfsm.start(TestContext())
        runner.feed(1)
        assertThat(timeSpent).isAtLeast(sleepTime)
        assertThat(conditionCalled).isTrue()
    }

    @Test
    fun shouldFeedASyncBeReturningImmediatelyWhenFedWithValue() {
        var conditionCalled = false
        var timeSpent = 0L
        val sleepTime = 200L
        val now = System.currentTimeMillis()
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 1
                        action {
                            // Simulate some processing
                            Thread.sleep(sleepTime)
                            conditionCalled = true
                            timeSpent = System.currentTimeMillis() - now
                        }
                    }
                }
            }
        }
        val runner = kfsm.start(TestContext())
        runBlocking {
            val j = runner.feedAsync(1)
            // Shouldn't be updated yet
            assertThat(timeSpent).isEqualTo(0L)
            assertThat(conditionCalled).isFalse()
            j.join()
            assertThat(timeSpent).isAtLeast(sleepTime)
            assertThat(conditionCalled).isTrue()
        }
    }

    @Test
    fun testShouldTransitionWithMoreThanOneMatchingPredicateFailAtRuntime() {
        var conditionCalled = false
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 1
                        condition { _, _ -> conditionCalled = true; true }
                    }
                    transition {
                        to = 2
                        condition { _, _ -> conditionCalled = true; true }
                    }
                }
            }
            state {
                // Since we disallow transitions to duplicate target state IDs from a given state, create two states
                id = 2
            }
        }
        val runner = kfsm.start(TestContext())
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            runner.feed(0)
        }
        assertThat(thrownError.message).isEqualTo("Multiple transition candidates found for input 0 at State(id=1, label=-, isSource=true, isSink=false)")
        assertThat(runner.started).isFalse()
        assertThat(conditionCalled).isTrue()
    }

    @Test
    fun testShouldTransitionWithNoMatchingPredicateFailAtRuntime() {
        var conditionCalled = false
        val kfsm = kfsm<TestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 1
                        condition { _, _ -> conditionCalled = true; false }
                    }
                }
            }
        }
        val runner = kfsm.start(TestContext())
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            runner.feed(0)
        }
        assertThat(thrownError.message).isEqualTo("No transition candidates found for input 0 at State(id=1, label=-, isSource=true, isSink=false)")
        assertThat(runner.started).isFalse()
        assertThat(conditionCalled).isTrue()
    }

    @Test
    fun shouldParityCheckFSMRunCorrectlyBasedOnInput() {
        val evenRunner = checkNumberParity.start(ParityCheck())
        val evenParity = listOf(1, 0, 0, 1, 1, 0, 1, 0)
        evenParity.forEachIndexed { idx, digit ->
            runBlocking {
                assertThat(evenRunner.feedAsync(idx to digit).await()).isTrue()
            }
            // Make sure they are processed in order
            assertThat(evenRunner.currentContext.index).isEqualTo(idx)
        }
        assertThat(evenRunner.currentContext.hasEvenNumberOfZeros).isTrue()
        assertThat(evenRunner.currentState.id.id).isEqualTo(1)
        assertThat(evenRunner.started).isTrue()
        evenRunner.stop()
        assertThat(evenRunner.started).isFalse()

        val oddRunner = checkNumberParity.start(ParityCheck())
        val oddParity = listOf(1, 0, 0, 1, 1, 0, 1, 1)
        oddParity.forEachIndexed { idx, digit ->
            runBlocking {
                assertThat(oddRunner.feedAsync(idx to digit).await()).isTrue()
            }
            // Make sure they are processed in order
            assertThat(oddRunner.currentContext.index).isEqualTo(idx)
        }
        assertThat(oddRunner.currentContext.hasEvenNumberOfZeros).isFalse()
        assertThat(oddRunner.currentState.id.id).isEqualTo(2)
        assertThat(oddRunner.started).isTrue()
        oddRunner.stop()
        assertThat(oddRunner.started).isFalse()

        val evenRunner2 = checkNumberParity.start(ParityCheck())
        val feedProducer = Channel<Pair<Int, Int>>()
        runBlocking {
            val job = evenRunner2.feedAsync(feedProducer)
            evenParity.forEachIndexed { idx, digit ->
                feedProducer.sendBlocking(idx to digit)
            }
            feedProducer.close()
            assertThat(job.await()).isTrue()
        }
        assertThat(evenRunner2.currentContext.hasEvenNumberOfZeros).isTrue()
        assertThat(evenRunner2.currentState.id.id).isEqualTo(1)
        assertThat(evenRunner2.started).isTrue()
        evenRunner2.stop()
        assertThat(evenRunner2.started).isFalse()
    }

    @Test
    fun shouldFeedThrowAnExceptionAfterRunnerIsStopped() {
        class FinalStateTestContext(var value: Int) : TestContext()

        val kfsm = kfsm<FinalStateTestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 2
                        actionWithState { _, runner ->
                            runner.currentContext.value = 99
                        }
                    }
                }
            }
            state {
                id = 2
            }
        }
        val runner = kfsm.start(FinalStateTestContext(0))
        runner.stop()
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            runner.feed(1)
        }
        // Ensure we can still access currentContext and currentState even after the runner is stopped
        with(runner.currentState) {
            assertThat(id.id).isEqualTo(1)
            assertThat(isSink).isFalse()
            assertThat(isSource).isTrue()
        }
        assertThat(runner.currentContext.value).isEqualTo(0)
        assertThat(thrownError.message).isEqualTo("The current runner is not running; cannot feed 1")
    }

    @Test
    fun shouldFeedThrowAnExceptionAfterRunnerReachesAFinalState() {
        class FinalStateTestContext(var value: Int) : TestContext()

        val kfsm = kfsm<FinalStateTestContext, Int> {
            initialState {
                id = 1
                transitions {
                    transition {
                        to = 2
                        actionWithState { _, runner ->
                            runner.currentContext.value = 99
                        }
                    }
                }
            }
            state {
                id = 2
            }
        }
        val runner = kfsm.start(FinalStateTestContext(0))
        runner.feed(1)
        val thrownError = assertThrows(
            IllegalStateException::class.java
        ) {
            runner.feed(1)
        }
        // Ensure we can still access currentContext and currentState even after the runner is stopped
        with(runner.currentState) {
            assertThat(id.id).isEqualTo(2)
            assertThat(isSink).isTrue()
            assertThat(isSource).isFalse()
        }
        assertThat(runner.currentContext.value).isEqualTo(99)
        assertThat(thrownError.message).isEqualTo("The current runner is not running; cannot feed 1")
    }
}