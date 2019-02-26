/*
 * MIT License
 *
 * Copyright (c) 2019 Yoann Mikami
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.ymkm.android.kfsm

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.Comparator
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KProperty

/**
 * kotlin delegate that ensures that the variable is assigned once (ie. not reassigned)
 */
sealed class OnceInitializerDelegate<T>(private val alreadySetMessage: String) {

    protected var value: T? = null

    operator fun setValue(thisRef: Any?, prop: KProperty<*>, value: T) {
        require(this.value == null) { alreadySetMessage }
        this.value = value
    }
}

/**
 * kotlin delegate that ensures a variable is either assigned only once or not set at all
 */
class OptionalOnceInitializerDelegate<T>(alreadySetMessage: String) : OnceInitializerDelegate<T>(alreadySetMessage) {

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T? {
        return value
    }
}

/**
 * kotlin delegate that ensures a variable is set only once to a non null value
 */
class RequiredOnceInitializerDelegate<T>(private val notSetMessage: String, alreadySetMessage: String) :
    OnceInitializerDelegate<T>(alreadySetMessage) {

    operator fun getValue(thisRef: Any?, prop: KProperty<*>): T {
        require(this.value != null) { notSetMessage }
        return value!!
    }
}

/**
 * Defines an action to perform when taking a feedAsync
 */
internal interface KTransitionPredicate<Context : FSMContext, Input> {

    /**
     * Action performed when a feedAsync is being taken
     *
     * @param input the input to apply the predicate on
     * @param runner the FSMRunner instance that wants to trigger the feedAsync
     * @return true if conditions are met to trigger the feedAsync, false otherwise
     */
    fun condition(input: Input?, runner: FSMRunner<Context, Input>): Boolean
}

/**
 * Defines an action to perform when taking a feedAsync
 */
internal interface KTransitionAction<Context : FSMContext, Input> {

    /**
     * Action performed when a feedAsync is being taken
     *
     * @param transition the feedAsync being taken
     * @param runner the FSMRunner instance that triggered the feedAsync
     */
    fun onTransition(transition: Transition, runner: FSMRunner<Context, Input>)
}

/**
 * Defines an action to perform when entering/exiting a state after a feedAsync
 */
internal interface KStateAction<Context : FSMContext, Input> {

    /**
     * Action performed when a entering a state
     *
     * @param runner the FSMRunner instance that triggered the action
     */
    fun onEnter(runner: FSMRunner<Context, Input>)

    /**
     * Action performed when a exiting a state
     *
     * @param runner the FSMRunner instance that triggered the action
     */
    fun onExit(runner: FSMRunner<Context, Input>)
}


class KTransition<Context : FSMContext, Input> private constructor(
    override val toStateId: StateId,
    override val label: String?,
    internal val transitionAction: KTransitionAction<Context, Input>?,
    internal val transitionCondition: KTransitionPredicate<Context, Input>?
) :
    Transition {

    override fun toString(): String {
        return "Transition(targetState=$toStateId, label=${label ?: "-"})"
    }

    class ListBuilder<Context : FSMContext, Input> : Transition.ListBuilder<Context, Input> {

        override fun transition(transitionBuilderBody: Transition.Builder<Context, Input>.() -> Unit) {
            transition(transitionBuilderBody, null)
        }

        override fun transition(toStateId: Int, label: String?) {
            transition(KTransition(
                StateId(toStateId), label,
                object : KTransitionAction<Context, Input> {

                    override fun onTransition(transition: Transition, runner: FSMRunner<Context, Input>) {
                        StateId(toStateId)
                    }
                },
                object : KTransitionPredicate<Context, Input> {
                    override fun condition(input: Input?, runner: FSMRunner<Context, Input>): Boolean = true
                }
            ))
        }

        override fun String.transition(transitionBuilderBody: Transition.Builder<Context, Input>.() -> Unit) {
            transition(transitionBuilderBody, this)
        }


        internal fun build(): List<Pair<Int, KTransition<Context, Input>>> {
            return transitionList.toList().map {
                it.first.id to it.second
            }
        }


        private fun transition(
            transitionBuilderBody: Transition.Builder<Context, Input>.() -> Unit,
            labelOverride: String? = null
        ) {
            val builder = Builder<Context, Input>()
            labelOverride?.run {
                builder.label = this
            }
            builder.transitionBuilderBody()
            val transition = builder.build()
            transition(transition)
        }

        private fun transition(transition: KTransition<Context, Input>) {
            check(!transitionList.containsKey(transition.toStateId)) {
                "$transition targets state ${transition.toStateId} already defined by previous transition definition : ${transitionList[transition.toStateId]}"
            }
            transitionList[transition.toStateId] = transition
        }


        private val transitionList = mutableMapOf<StateId, KTransition<Context, Input>>()
    }

    class Builder<Context : FSMContext, Input> : Transition.Builder<Context, Input> {

        override var to: Int by RequiredOnceInitializerDelegate(
            "Target state ID is required",
            "Target state ID already set"
        )
        override var label: String? by OptionalOnceInitializerDelegate("Transition label already set")

        override fun action(body: (() -> Unit)) {
            transitionAction = object : KTransitionAction<Context, Input> {
                override fun onTransition(transition: Transition, runner: FSMRunner<Context, Input>) {
                    body()
                }
            }
        }

        override fun actionWithState(transitionAction: ((Transition, FSMRunner<Context, Input>) -> Unit)) {
            this.transitionAction = object : KTransitionAction<Context, Input> {
                override fun onTransition(transition: Transition, runner: FSMRunner<Context, Input>) {
                    transitionAction(transition, runner)
                }
            }
        }

        override fun condition(predicate: (input: Input?, runner: FSMRunner<Context, Input>) -> Boolean) {
            transitionCondition = object : KTransitionPredicate<Context, Input> {
                override fun condition(input: Input?, runner: FSMRunner<Context, Input>): Boolean =
                    predicate(input, runner)
            }
        }


        internal fun build(): KTransition<Context, Input> {
            return KTransition(StateId(to), label, transitionAction, transitionCondition)
        }


        private var transitionCondition: KTransitionPredicate<Context, Input>? by OptionalOnceInitializerDelegate("Transition predicate already defined")
        private var transitionAction: KTransitionAction<Context, Input>? by OptionalOnceInitializerDelegate("Transition action already defined")
    }
}


class KState<Context : FSMContext, Input> private constructor(
    override val id: StateId,
    override val label: String?,
    override val isSource: Boolean,
    private val stateAction: KStateAction<Context, Input>?
) :
    State {

    override fun toString(): String {
        return "State(id=${id.id}, label=${label ?: "-"}, isSource=$isSource, isSink=$isSink)"
    }

    override val isSink: Boolean
        get() = allowedTransitions.isEmpty()


    internal fun onEnter(runner: FSMRunner<Context, Input>) {
        stateAction?.onEnter(runner)
    }

    internal fun onExit(runner: FSMRunner<Context, Input>) {
        stateAction?.onExit(runner)
    }

    internal fun onTransition(targetStateId: StateId, runner: FSMRunner<Context, Input>): KTransition<Context, Input> {
        val transition = transitionFromTargetStateId(targetStateId)?.run {
            transitionAction?.onTransition(this, runner)
            this
        }
        checkNotNull(transition) { "Transition not allowed: target state $targetStateId from: $this" }
        return transition
    }

    @get:JvmSynthetic
    internal val allowedTransitions = HashSet<KTransition<Context, Input>>()


    private fun transitionFromTargetStateId(targetStateId: StateId): KTransition<Context, Input>? =
        allowedTransitions.find { it.toStateId == targetStateId }

    private fun transition(transition: KTransition<Context, Input>): KState<Context, Input> {
        allowedTransitions.add(transition)
        return this
    }


    class Builder<Context : FSMContext, Input>(private val isSource: Boolean) : State.Builder<Context, Input> {

        override var id: Int by RequiredOnceInitializerDelegate("State ID is required", "State ID already set")
        override var label: String? by OptionalOnceInitializerDelegate("State label already set")

        override fun enter(body: () -> Unit) {
            stateAction.enterAction = { _ ->
                body()
            }
        }

        override fun enterWithState(enterAction: (FSMRunner<Context, Input>) -> Unit) {
            stateAction.enterAction = enterAction
        }

        override fun exit(body: () -> Unit) {
            stateAction.exitAction = { _ ->
                body()
            }
        }

        override fun exitWithState(exitAction: (FSMRunner<Context, Input>) -> Unit) {
            stateAction.exitAction = exitAction
        }

        override fun transitions(transitionBuilderBlock: Transition.ListBuilder<Context, Input>.() -> Unit) {
            transitionListBuilder.transitionBuilderBlock()
        }


        internal fun build(): KState<Context, Input> {
            return KState(StateId(id), label, isSource, stateAction).apply {
                transitionListBuilder.build().forEach {
                    transition(it.second)
                }
            }
        }

        private val stateAction: KStateActionInner<Context, Input> by lazy { KStateActionInner<Context, Input>() }
        private val transitionListBuilder: KTransition.ListBuilder<Context, Input> by lazy { KTransition.ListBuilder<Context, Input>() }

        private class KStateActionInner<Context : FSMContext, Input> : KStateAction<Context, Input> {
            override fun onEnter(runner: FSMRunner<Context, Input>) {
                enterAction?.run {
                    this(runner)
                }
            }

            override fun onExit(runner: FSMRunner<Context, Input>) {
                exitAction?.run {
                    this(runner)
                }
            }

            @get:JvmSynthetic
            @set:JvmSynthetic
            internal var enterAction: ((FSMRunner<Context, Input>) -> Unit)? by OptionalOnceInitializerDelegate("Enter state action already defined")
            @get:JvmSynthetic
            @set:JvmSynthetic
            internal var exitAction: ((FSMRunner<Context, Input>) -> Unit)? by OptionalOnceInitializerDelegate("Exit state action already defined")
        }
    }
}

class KFSM<Context : FSMContext, Input> internal constructor(kFSMBuilder: Builder<Context, Input>.() -> Unit) :
    FSM<Context, Input> {

    object Debug {
        internal var debug: Boolean = false

        fun setDebug(debug: Boolean) {
            Debug.debug = debug
        }
    }

    companion object {
        private val TAG = KFSM::class.java.simpleName
        private fun debugLog(msg: String) {
            if (KFSM.Debug.debug) {
                Log.d(TAG, msg)
                System.out.println(msg)
            }
        }
    }


    class Builder<Context : FSMContext, Input> : FSM.Builder<Context, Input> {

        @get:JvmSynthetic
        @set:JvmSynthetic
        internal var initialStateSet: Boolean = false
        @get:JvmSynthetic
        internal val statesB = HashSet<KState<Context, Input>>()

        override fun initialState(initialStateBuilderBody: State.Builder<Context, Input>.() -> Unit) {
            initialState(initialStateBuilderBody, null)
        }

        override fun state(stateBuilderBody: State.Builder<Context, Input>.() -> Unit) {
            state(stateBuilderBody, null)
        }

        override infix fun String.initialState(initialStateBuilderBody: State.Builder<Context, Input>.() -> Unit) {
            initialState(initialStateBuilderBody, this)
        }

        override infix fun String.state(stateBuilderBody: State.Builder<Context, Input>.() -> Unit) {
            state(stateBuilderBody, this)
        }


        private fun initialState(
            initialStateBuilderBody: State.Builder<Context, Input>.() -> Unit,
            labelOverride: String? = null
        ) {
            require(!initialStateSet) { error("Only one initial state is allowed within a graph") }
            state(KState.Builder(true), initialStateBuilderBody, labelOverride)
            initialStateSet = true
        }

        private fun state(stateBuilderBody: State.Builder<Context, Input>.() -> Unit, labelOverride: String? = null) {
            state(KState.Builder(false), stateBuilderBody, labelOverride)
        }

        private fun state(
            stateBuilder: KState.Builder<Context, Input>,
            stateBuilderBody: State.Builder<Context, Input>.() -> Unit,
            labelOverride: String? = null
        ) {
            stateBuilder.stateBuilderBody()
            labelOverride?.run {
                stateBuilder.label = this
            }
            val state = stateBuilder.build()
            require(statesB.find { it.id == state.id } == null) { "State $state : Duplicate ${state.id} found in graph" }
            statesB.add(state)
        }
    }


    override fun toString(): String {
        return "FSM With ${states.size} states.\n" + states.joinToString(
            prefix = "\n-> ",
            separator = "\n-> "
        ) { s -> s.toString() }
    }

    @Synchronized
    override fun start(initialContext: Context, observer: FSMRunner.Observer?): FSMRunner<Context, Input> {
        debugLog("KFSM start")
        return KFSMRunner(this, states, initialContext, observer)
    }


    internal class KFSMRunner<Context : FSMContext, Input>(
        override val fsm: FSM<Context, Input>,
        private val states: Collection<KState<Context, Input>>, initialContext: Context,
        private val observer: FSMRunner.Observer? = null
    ) :
        FSMRunner<Context, Input>, CoroutineScope {

        override lateinit var currentState: KState<Context, Input>
            private set

        override var currentContext = initialContext
            private set

        override var started = false
        private val feedChannel = Channel<Pair<Input, Context>>()
        private val resultChannel = Channel<Throwable?>()
        private var job: Job

        init {
            // Find the initial state
            val initialState = states.find { it.isSource }

            check(initialState != null) { "Could not find initial state" }

            started = true
            currentState = initialState
            observer?.onStarted()
            currentState.onEnter(this@KFSMRunner)
            observer?.onStateEnter(currentState)

            job = GlobalScope.async {
                var reachedFinalState = false
                try {
                    while (isActive && !reachedFinalState) {
                        val pair = feedChannel.receive()
                        debugLog("KFSM feeding input : ${pair.first}")
                        check(started) { "Tried to feedAsync ${pair.first} while not started" }
                        processStateChange(pair)
                        if (currentState.isSink) {
                            reachedFinalState = true
                        }
                        resultChannel.send(null)
                    }
                } catch (stopped: ClosedReceiveChannelException) {
                    // Channel was closed due to a call to stop
                    debugLog("feedChannel was closed")
                } catch (throwable: Throwable) {
                    debugLog("Error while trying to process next input: $throwable")
                    observer?.onError(throwable)
                    resultChannel.send(throwable)
                } finally {
                    observer?.onEnded()
                    started = false
                    debugLog("KFSM end")
                }
            }
        }

        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Default + job

        override fun feedAsync(input: Input): Deferred<Boolean> {
            check(started) { "The current runner is not running; cannot feedAsync $input" }
            return async {
                feedChannel.send(input to currentContext)
                val t = resultChannel.receive()
                if (t != null) {
                    throw t
                }
                true
            }
        }

        @ObsoleteCoroutinesApi
        override fun feedAsync(inputChannel: ReceiveChannel<Input>): Deferred<Boolean> {
            return async {
                inputChannel.consumeEach {
                    feedChannel.send(it to currentContext)
                    val t = resultChannel.receive()
                    if (t != null) {
                        throw t
                    }
                }
                true
            }
        }

        override fun stop() {
            runBlocking {
                val j = async {
                    try {
                        feedChannel.close()
                        resultChannel.close()
                        job.cancelAndJoin()
                    } catch (t: Throwable) {
                        throw t
                    }
                }
                j.await()
            }
        }

        override fun availableTransitions(): List<Transition> {
            return currentState.allowedTransitions.toList()
        }


        private fun processStateChange(pair: Pair<Input, Context>) {
            currentState.onExit(this@KFSMRunner)
            val candidatesTransitions = currentState.allowedTransitions.map {
                (it.transitionCondition?.condition(pair.first, this@KFSMRunner)
                    ?: true) to it.toStateId
            }
            val candidatesCount = candidatesTransitions.filter { it.first }.count()
            when {
                candidatesCount == 0 -> error("No transition candidates found for input ${pair.first} at $currentState")
                candidatesCount > 1 -> error("Multiple transition candidates found for input ${pair.first} at $currentState")
            }
            observer?.onStateExit(currentState)

            val transition: KTransition<Context, Input> =
                currentState.onTransition(candidatesTransitions.find { it.first }!!.second, this@KFSMRunner)
            observer?.onTransition(transition)

            val oldState = currentState
            val newState = states.find { it.id == transition.toStateId }
            // This should not happen since the transitions targets are checked during the FSM build
            check(newState != null) { "Cannot use transition $transition : no target state ${transition.toStateId} defined in the graph." }
            newState.run {
                currentState = this
                this
            }.onEnter(this@KFSMRunner)

            observer?.onStateEnter(newState)
            debugLog("[Transition] $oldState ---- $transition ----> $newState")
        }
    }


    private val states = TreeSet<KState<Context, Input>>(Comparator<KState<Context, Input>> { o1, o2 -> o1.id - o2.id })


    init {
        val builder = Builder<Context, Input>()
        builder.apply {
            kFSMBuilder()
            check(!statesB.isEmpty()) { "Cannot create a KFSM without any state" }
            check(initialStateSet) { "Cannot create a KFSM without a start state" }
            states.addAll(statesB)
            states.forEach { state ->
                state.allowedTransitions.forEach { transition ->
                    checkNotNull(states.find { s -> s.id == transition.toStateId }) {
                        "$transition : Target state ${transition.toStateId} undefined in graph"
                    }
                }
            }
        }
    }
}
