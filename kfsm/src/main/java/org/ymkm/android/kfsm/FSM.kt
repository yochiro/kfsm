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

@file:Suppress("SpellCheckingInspection")

package org.ymkm.android.kfsm

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Base interface for arbitrary context objects to be passed to [FSM] upon creation.
 *
 * Acts as a global state that can be read/written within each defined state/transition action functions.
 */
interface FSMContext

/**
 * Data class defining a state ID
 *
 * @property id The state ID. Should be unique in the FSM
 * @constructor creates a new state ID
 */
data class StateId(val id: Int) {
    operator fun minus(other: StateId): Int = id - other.id
    operator fun plus(other: StateId): Int = id + other.id
}


/**
 * Interface that defines a (non-deterministic) [finite state machine](https://en.wikipedia.org/wiki/Finite-state_machine)
 *
 * # Overview
 *
 * The FSM structure must be specified during construction and is fixed after its creations.
 * Whenever starting the FSM, a new [FSMRunner] instance is created, the FSM is initialized to its initial state.
 * [FSMRunner] can then be fed input by calling [FSMRunner.feedAsync]; the FSM will transition to a new state based on the current state and its available transitions.
 * [FSMRunner] can also be used to get the [FSMRunner.currentState], the [FSMRunner.currentContext] or list [FSMRunner.availableTransitions] from [FSMRunner.currentState].
 *
 * # FSM Context
 *
 * A [FSMContext] is an arbitrary object that is passed when [FSM.start]ing the FSM that defines properties to be shared by the [FSMRunner] during
 * its lifecycle; the context instance can be queried, updated in actions triggered on [State.Builder.enterWithState] or [State.Builder.exitWithState], on actions
 * during [Transition.Builder.actionWithState] to maintain the state of the current running instance.
 *
 * # Creation
 *
 * To create a new FSM, use the [kfsm] function; its argument is a block that defines all states (themselves defining transitions) available within the FSM.
 * Upon creating the FSM, static checks are performed to ensure that no [StateId.id] are duplicated and that all [Transition] outgoing from all states target a [StateId]
 * defined in the fsm block. An [FSM] must always define an [FSM.Builder.initialState]. This state is the one which the [FSMRunner]
 * will enter when the FSM is started.
 *
 * # Sample
 *
 * ## Context data model
 * Define an [FSMContext] subclass to use. It may be an empty object if not required.
 * ```kotlin
 * data class MyContext(var count: Int) : FSMContext
 * ```
 * ## FSM Creation
 *
 * Create a new FSM definition by using the code below :
 *
 * ```kotlin
 *  val myfsm = kfsm<MyContext> {
 *
 *      ... fsm definition ...
 *
 *  }
 *  ```
 *
 *  ## Specify FSM states
 *
 * *An [kfsm] requires at least an initial state. It must have at least 1 state (including the initial state)*
 *
 * The [kfsm] function takes a block where the states, including the initial state, can be defined. Each state/initial state requires at least an [State.Builder.id].
 *
 * There are two ways to define a state/initial state inside an FSM.
 * - Use the [FSM.Builder.state] function defined at the block level
 * E.g.
 * ```kotlin
 * kfsm<MyContext> {
 *     state {
 *         id = 1
 *         label = "State label" // Optional
 *     }
 * }
 * ```
 * - Use the string extension [FSM.Builder.state] defined at the block level. The string defines the label for the state.
 * E.g.
 * ```kotlin
 * kfsm<MyContext> {
 *     "State label" state {
 *         id = 1
 *     }
 * }
 * ```
 *
 * _To specify the initial state, use resp. the [FSM.Builder.initialState] or [FSM.Builder.initialState] functions instead._
 *
 * ## Specify state enter/exit actions
 *
 * Each state can optionally be setup with an enter and/or exit action. The enter action is triggered when a transition reaches the state; the exit action is triggered when leaving the state due to a transition.
 * The initial state enter action will be triggered as soon as the FSM is started.
 *
 * There are two variants available, depending on whether the [FSMRunner.currentContext], [FSMRunner.currentState] or [FSMRunner.availableTransitions] are required for processing.
 *
 * - [State.Builder.enter] | [State.Builder.exit] -> A no arg function block to specify an action without any dependency on [FSMRunner]
 * - [State.Builder.enterWithState] | [State.Builder.exitWithState] -> A 1-arg function block to specify an action which gets passed the [FSMRunner] for the current running instance.
 *
 * ## Specify outgoing transitions for each state
 *
 * Each non-final state will have at least an outgoing transition to another state, while final state (sinks) only have ingoing transitions.
 *
 * To define transitions, use the [KState.Builder.transitions] block :
 * ```kotlin
 * kfsm<MyContext> {
 *     state {
 *         id = 1
 *         label = "State label"
 *         transitions {
 *             ... define outgoing transitions here ...
 *         }
 *     }
 * }
 * ```
 *
 * Inside the transitions block, there are 3 ways to define a transition :
 * - For simple transitions (no action, no conditions), use the [KTransition.ListBuilder.transition] that takes 2 mandatory arguments and an optional label :
 * ```kotlin
 * transitions {
 *     transition(3) // target state ID 3, without a label
 *     transition(4, "Target State 4") // target state ID 4, with a transition label
 * }
 * ```
 * - Use the variant [KTransition.ListBuilder.transition] that can take an optional action block and/or condition :
 * ```kotlin
 * transitions {
 *     transition {
 *         to = 2 // Target state ID when using this transition
 *         label = "Transition label" // Optional
 *         action | actionWithState {
 *             ....
 *         }
 *         condition { input, context ->
 *             ....
 *         }
 *     }
 * }
 * ```
 *  - Use the string extension [Transition.ListBuilder.transition] defined at the block level. The string defines the label for the transition.
 *  E.g.
 *  ```kotlin
 *  transitions {
 *      "Transition label" transition {
 *          to = 2
 *          action | actionWithState { // Optional
 *              ....
 *          }
 *          condition { input, runner ->
 *              ....
 *          }
 *      }
 *  }
 *  ```
 *  ## Specify transition actions
 *
 *  Each transition can optionally be setup with an action to be performed when the transition is taken.
 *
 *  There are two variants available, depending on whether the [FSMRunner.currentContext], [FSMRunner.currentState] or [FSMRunner.availableTransitions] are required for processing.
 *
 *  - [Transition.Builder.action] -> A no arg function block to specify an action without any dependency on [FSMRunner]
 *  - [Transition.Builder.actionWithState] -> A 2-arg function block to specify an action which gets passed the [Transition] currently in progress, and the [FSMRunner] for the current running instance.
 *
 * ## Specify transition condition
 *
 * A transition can be bound to an optional predicate function that is checked for each input given; if the predicate returns true, then the transition satisfies the conditions required to change state to the specified target.
 *
 * If more than one transition is defined on the current state while a new input is given, exactly 1 transition predicate should return true. Failing to do so will result in a runtime error.
 *
 * Define a transition predicate like follow (in this case, the input type is a String) :
 * E.g.
 * ```kotlin
 * transitions {
 *     "Transition if starts with letter a" transition {
 *         to = 2
 *         condition { input, runner ->
 *             return input.startsWith("a")
 *         }
 *     }
 *     "Transition if starts with letter b" transition {
 *         to = 3
 *         condition { input, runner ->
 *             return input.startsWith("b")
 *         }
 *     }
 *     ... etc ...
 * }
 *
 * ...
 *
 * runner.feedAsync("a string") // Will use transition 1 and go to state 2, asynchronously.
 * ```
 *
 *
 *  # Running
 *
 *  [FSM] instances only define the structure of the graph, and are stateless. To create a new running instance, use the [FSM.start] function.
 *  You must pass in the initial [FSMContext] value, as well as an optional [FSMRunner.Observer] object. The latter will receive events as the FSM transitions from one state to another.
 *  The return value is a new instance of [FSMRunner] that can be used to transition, or query the current state of the running instance.
 *  Multiple call to [FSM.start] are allowed, and each will yield a new instance running.
 *
 *  ## Transition
 *
 * Use the [FSMRunner.feedAsync] function to feed in some new input that should internally yield in a transition to another state. An error will be raised if the input specified cannot reduce to a single transition at current state.
 * If a single transition exists that qualifies for given input, the following call sequence will be performed, in that order :
 *
 *  - [State.Builder.exit] | [State.Builder.exitWithState], if defined on [FSMRunner.currentState]
 *  - [Transition.Builder.action] | [Transition.Builder.actionWithState], if defined for the transition specified.
 *  - [State.Builder.enter] | [State.Builder.enterWithState], if defined on the [Transition.toStateId]
 *
 *  When reaching such final states, the [FSMRunner] ends and any further call to [FSMRunner.feedAsync] will throw an [IllegalStateException]. The final state will be available through [FSMRunner.currentState], while the last state of [FSMContext] will be available through [FSMRunner.currentContext].
 *
 * # Example
 *
 * Following the example given on the [Wikipedia page](https://en.wikipedia.org/wiki/File:DFAexample.svg) for a FSM that checks if given integer in binary has an even number of zeros
 *
 * ```kotlin
 * data class CheckParity(var hasEvenNumberOfZeros: Boolean = false) : FSMContext
 *
 * val checkNumberParity = kfsm<MyContext, Int> {
 *     "S1" initialState {
 *         id = 1
 *         enterWithState { runner ->
 *             runner.currentContext.hasEvenNumberOfZeros = true
 *         }
 *         transitions {
 *             "Got 0" transition {
 *                 to = 2
 *                 condition { input, _ ->
 *                     input == 0
 *                 }
 *             }
 *             "Got 1" transition {
 *                 to = 1
 *                 condition { input, _ ->
 *                     input == 1
 *                 }
 *             }
 *         }
 *     }
 *     "S2" state {
 *         id = 2
 *         enterWithState { runner ->
 *             runner.currentContext.hasEvenNumberOfZeros = false
 *         }
 *         transitions {
 *             "Got 0" transition {
 *                 to = 1
 *                 condition { input, _ ->
 *                     input == 0
 *                 }
 *             }
 *             "Got 1" transition {
 *                 to = 2
 *                 condition { input, _ ->
 *                     input == 1
 *                 }
 *             }
 *         }
 *     }
 * }
 * ```
 */
interface FSM<Context : FSMContext, Input> {

    /**
     * Starts a new [FSMRunner] instance on this FSM.
     *
     * @param initialContext the initial context to use during start
     * @param observer optional observer to get state/transition change events
     * @return a new [FSMRunner] instance to feed input to
     */
    fun start(initialContext: Context, observer: FSMRunner.Observer? = null): FSMRunner<Context, Input>

    /**
     * Interface for a builder that creates [FSM] instances
     *
     * @throws IllegalArgumentException if required parameters are not set
     */
    interface Builder<Context : FSMContext, Input> {

        /**
         * Sets the initial state the [FSM] should be started on. *Required*
         *
         * @param initialStateBuilderBody the initialization block
         * @throws IllegalArgumentException if initialState is called more than once (more than one initialState is specified)
         * @throws IllegalArgumentException if specified [StateId] is a duplicate, if it is missing or if it is defined more than once for a state
         */
        fun initialState(initialStateBuilderBody: State.Builder<Context, Input>.() -> Unit)

        /**
         * Adds a state to the [FSM].
         *
         * Each state should have a unique ID within a given FSM, or an [IllegalArgumentException] will be thrown.
         *
         * @param stateBuilderBody the initialization block
         * @throws IllegalArgumentException if specified [StateId] is a duplicate, if it is missing or if it is defined more than once for a state
         */
        fun state(stateBuilderBody: State.Builder<Context, Input>.() -> Unit)

        /**
         * Infix variant of [initialState] as a string extension method.
         *
         * The string parameter represents be the [State.label]. An [IllegalArgumentException] will be thrown if the label is also set within the initialization block.
         *
         * Example :
         *
         * ```kotlin
         * "my state label" initialState {
         *     // ... initialization block ... //
         * }
         * ```
         *
         * @param initialStateBuilderBody the initialization block
         * @throws IllegalArgumentException if initialState is called more than once (more than one initialState is specified)
         * @throws IllegalArgumentException if label is set within the initialization block
         * @throws IllegalArgumentException if specified [StateId] is a duplicate, if it is missing or if it is defined more than once for a state
         */
        infix fun String.initialState(initialStateBuilderBody: State.Builder<Context, Input>.() -> Unit)

        /**
         * Infix variant of [state] as a string extension method.
         *
         * The string parameter represents be the [State.label]. An [IllegalArgumentException] will be thrown if the label is also set within the initialization block.
         *
         * Example :
         *
         * ```kotlin
         * "my state label" state {
         *     // ... initialization block ... //
         * }
         * ```
         *
         * @param stateBuilderBody the initialization block
         * @throws IllegalArgumentException if label is set within the initialization block
         * @throws IllegalArgumentException if specified [StateId] is a duplicate, if it is missing or if it is defined more than once for a state
         */
        infix fun String.state(stateBuilderBody: State.Builder<Context, Input>.() -> Unit)
    }
}

/**
 * Represents a running instance of a [FSM]. Multiple [FSMRunner] can be running independently at the same time on the same [FSM].
 *
 * Use the [FSMRunner] to query the [FSMRunner.currentState] of the FSM sequence, [FSMRunner.feedAsync] some new input or [FSMRunner.stop] the sequence.
 */
interface FSMRunner<Context : FSMContext, Input> {

    /**
     * Current context value
     */
    val currentContext: Context
    /**
     * Current state
     */
    val currentState: State

    /**
     * The [FSM] the runner is bound to
     */
    val fsm: FSM<Context, Input>

    /**
     * Whether the runner is running. [FSMRunner.feedAsync] will throw [IllegalStateException] if called while [started] is false
     */
    val started: Boolean

    /**
     * Feed some new input to the [FSM]. Transition should occur internally based on the current state.
     *
     * The processing of the input is performed *synchronously*, so any long processing would block the main thread until it finishes.
     *
     * @param input the new input to feed
     * @return true if process was sucessful, false otherwise.
     * @throws IllegalStateException if no transition can be taken in the current state based on the specified input
     * @throws IllegalStateException if more than one transition can be taken as the result of feeding specified input
     */
    fun feed(input: Input): Boolean

    /**
     * Feed some new input to the [FSM]. Transition should occur internally based on the current state.
     *
     * The processing of the input is performed asynchronously, so the caller may not see the effect of the state change immediately after sending the input.
     *
     * To keep the FSM state consistent, it does not currently handle cancellation.
     *
     * The boolean value returned by the [Deferred] object will be true if the transition was successful, false if an error occurred.
     *
     * @param input the new input to feed
     * @return the deferred object; the caller can use to wait for sent inputs to be processed using [Deferred.join] or [Deferred.await].
     * @throws IllegalStateException if no transition can be taken in the current state based on the specified input
     * @throws IllegalStateException if more than one transition can be taken as the result of feeding specified input
     */
    fun feedAsync(input: Input): Deferred<Boolean>

    /**
     * Feed input through a kotlin Channel. The caller is responsible for closing the stream once it has completed.
     *
     * The processing of inputs is performed asynchronously, so the caller may not see the effect of the state change immediately after sending a new input.
     *
     * To keep the FSM state consistent, it does not currently handle cancellation.
     *
     * The boolean value returned by the [Deferred] object will be true if the transition was successful, false if an error occurred.
     *
     * @param inputChannel the channel to receive data from
     * @return the deferred object; the caller can use to wait for sent inputs to be processed using [Deferred.join] or [Deferred.await].
     * @throws IllegalStateException if no transition can be taken in the current state based on the specified input
     * @throws IllegalStateException if more than one transition can be taken as the result of feeding specified input
     */
    fun feedAsync(inputChannel: ReceiveChannel<Input>): Deferred<Boolean>

    /**
     * Stops running the current instance at its current state.
     *
     * [currentContext] and [currentState] will still be available for query after being stopped. Any further call to [FSMRunner.feedAsync] will result in a no-op.
     */
    fun stop()

    /**
     * Returns the list of [Transition] available at the [currentState].
     *
     * @return list of transitions available at the [currentState], or an empty list if [currentState] is [State.isSink].
     */
    fun availableTransitions(): List<Transition>

    /**
     * Observer that receives events caused by [FSMRunner] running state changes, error or state/transition being triggered on new input fed
     */
    interface Observer {
        /**
         * Called after [FSM.start] is called
         */
        fun onStarted()

        /**
         * Called after [FSMRunner.stop] is called, or if the current sequence has reached a final state ([State.isSink] is true)
         */
        fun onEnded()

        /**
         * Called when entering a state after a transition
         *
         * @param enteredState the state entered
         */
        fun onStateEnter(enteredState: State)

        /**
         * Called when exiting a state before a transition
         *
         * @param exitedState the state exited
         */
        fun onStateExit(exitedState: State)

        /**
         * Called when taking a transition from a state to another
         *
         * @param transition the transition being taken
         */
        fun onTransition(transition: Transition)

        /**
         * Called on error, e.g. when fed input cannot be processed by current state. The [FSMRunner] will be stopped at this point.
         *
         * @param error the Throwable that caused the error
         */
        fun onError(error: Throwable)
    }
}

/**
 * Defines a state in the [FSM]
 *
 * - Each state must have an ID uniquely defined within the graph.
 * - Each state may have a label that can be used for debugging purposes.
 * - One state within a given [FSM] is a source ([isSource] is true). A source is added through [FSM.Builder.initialState].
 * - Any state without outgoing transitions is a final state ([isSink] is true). Whenever reaching such states, the [FSMRunner] will stop.
 * - Any non-final state must have at least one outgoing transition, and zero or more incoming transitions.
 * - Each state can optionally have an enter and/or exit action function, to be triggered when resp. entering/exiting the state due to a transition.
 */
interface State {

    /**
     * The state ID (uniquely defined within each [FSM])
     */
    val id: StateId
    /**
     * The optional state label
     */
    val label: String?
    /**
     * Whether this is the initial state to start with when [FSM.start] starts a new [FSMRunner] instance
     */
    val isSource: Boolean
    /**
     * Whether this is a final state (ie. no outgoing transitions)
     */
    val isSink: Boolean

    /**
     * Interface for a builder that creates [State] instances
     *
     * @throws IllegalArgumentException if required parameters are not set
     */
    interface Builder<Context : FSMContext, Input> {

        /**
         * Sets the state ID.
         *
         * @throws IllegalArgumentException if the ID was already defined using the same Builder
         * @throws IllegalArgumentException if the ID is not set
         * @throws IllegalArgumentException if the ID set is a duplicate of another [StateId] within the same [FSM]
         */
        var id: Int
        /**
         * Sets the state label.
         *
         * @throws IllegalArgumentException if the label was already defined using the same Builder
         */
        var label: String?

        /**
         * Sets an action to perform when entering the state.
         *
         * The function provided cannot change the [FSMContext] or access the current [FSMRunner] state.
         *
         * @param body the function to perform
         * @throws IllegalArgumentException if an enter function was already specified in the same Builder
         */
        fun enter(body: () -> Unit)

        /**
         * Sets an action to perform when entering the state.
         *
         * The function provided gets passed [FSMRunner] as an argument, which allows it to access
         * the [FSMRunner.currentState] or [FSMRunner.currentContext].
         *
         * @param enterAction the function to perform. Receives the current [FSMRunner] as its parameter
         * @throws IllegalArgumentException if an enter function was already specified in the same Builder
         */
        fun enterWithState(enterAction: (runner: FSMRunner<Context, Input>) -> Unit)

        /**
         * Sets an action to perform when exiting the state.
         *
         * The function provided cannot change the [FSMContext] or access the current [FSMRunner] state.
         *
         * @param body the function to perform
         * @throws IllegalArgumentException if an exit function was already specified in the same Builder
         */
        fun exit(body: () -> Unit)

        /**
         * Sets an action to perform when entering the state.
         *
         * The function provided gets passed [FSMRunner] as an argument, which allows it to access
         * the [FSMRunner.currentState] or [FSMRunner.currentContext].
         *
         * @param exitAction the function to perform. Receives the current [FSMRunner] as its parameter
         * @throws IllegalArgumentException if an enter function was already specified in the same Builder
         */
        fun exitWithState(exitAction: (runner: FSMRunner<Context, Input>) -> Unit)

        /**
         * Sets outgoing transitions from this state
         *
         * @param transitionBuilderBlock the transitions initialization block
         * @throws IllegalStateException if two or more defined transitions transition to the same [StateId]
         */
        fun transitions(transitionBuilderBlock: Transition.ListBuilder<Context, Input>.() -> Unit)
    }
}

/**
 * Defines a transition between two states in a [FSM].
 *
 * - Each transition must have a target [StateId] which does not overlap another transition going out from the same state.
 * - Each transition may have a label that can be used for debugging purposes.
 * - Each transition can optionally have an action function, to be triggered when taking the transition.
 */
interface Transition {

    /**
     * The state ID to transition to when taking the transition.
     *
     * For a given state, its outgoing transitions must all target unique state IDs.
     */
    val toStateId: StateId
    /**
     * The optional state label
     */
    val label: String?


    /**
     * Interface for a builder that creates outgoing [Transition] from a given state.
     *
     * @throws IllegalStateException if more than one transition coming out from the same state target the same [StateId].
     */
    interface ListBuilder<Context : FSMContext, Input> {

        /**
         * Adds an outgoing transition from current state.
         *
         * Each transition must have a target [StateId] that does not overlap with other transitions going out from the same state, or an [IllegalStateException] will be thrown.
         *
         * @param transitionBuilderBody the initialization block
         */
        fun transition(transitionBuilderBody: Builder<Context, Input>.() -> Unit)

        /**
         * Adds an outgoing transition from current state to specified [StateId].
         *
         * Each transition must have a target [StateId] that does not overlap with other transitions going out from the same state, or an [IllegalStateException] will be thrown.
         *
         * @param toStateId the [StateId] to transition to
         * @param label Optional transition label
         */
        fun transition(toStateId: Int, label: String? = null)

        /**
         * Infix variant of [transition] as a string extension method.
         *
         * The string parameter represents be the [Transition.label]. An [IllegalArgumentException] will be thrown if the label is also set within the initialization block.
         *
         * Example :
         *
         * ```kotlin
         * "my transition label" transition {
         *     // ... initialization block ... //
         * }
         * ```
         *
         * @param transitionBuilderBody the initialization block
         * @throws IllegalArgumentException if label is set within the initialization block
         */
        infix fun String.transition(transitionBuilderBody: Builder<Context, Input>.() -> Unit)
    }

    /**
     * Interface for a builder that creates a [Transition].
     *
     * @throws IllegalArgumentException if required parameters are not set
     */
    interface Builder<Context : FSMContext, Input> {

        /**
         * Sets the target [StateId] to transition to.
         *
         * @throws IllegalArgumentException if the ID was already defined using the same Builder
         * @throws IllegalArgumentException if the ID is not set
         */
        var to: Int
        /**
         * Sets the transition label.
         *
         * @throws IllegalArgumentException if the label was already defined using the same Builder
         */
        var label: String?

        /**
         * Sets a predicate function that, for given input, returns true if conditions are met to take the transition.
         *
         * For a set of transitions coming out of the same state, conditions must be mutually exclusive for a given input.
         * An [IllegalStateException] will be thrown if more than one transition can be taken given the same input.
         * Similarily, an [IllegalStateException] will be thrown if no candidate transition could be found.
         *
         * _If a given input should keep the current state, a loop transition going from current state to itself should be added._
         *
         * @param predicate the predicate function that returns true if the transition should be taken, false otherwise
         * @throws IllegalArgumentException if a predicate function was already specified in the same Builder
         */
        fun condition(predicate: (input: Input?, runner: FSMRunner<Context, Input>) -> Boolean)

        /**
         * Sets an action to perform when taking the transition.
         *
         * The function provided cannot change the [FSMContext] or access the current [FSMRunner] state.
         *
         * @param body the function to perform
         * @throws IllegalArgumentException if an action function was already specified in the same Builder
         */
        fun action(body: (() -> Unit))

        /**
         * Sets an action to perform when taking the transition.
         *
         * The function provided gets passed [FSMRunner] as an argument, which allows it to access
         * the [FSMRunner.currentState] or [FSMRunner.currentContext].
         *
         * @param transitionAction the function to perform. Receives the [Transition] being taken and the current [FSMRunner] as its parameters
         * @throws IllegalArgumentException if an action function was already specified in the same Builder
         */
        fun actionWithState(transitionAction: (transition: Transition, runner: FSMRunner<Context, Input>) -> Unit)
    }
}

/**
 * Creates a new FSM through the supplied initialization block
 *
 * @param kFSMBuilder the [FSM.Builder] as an initialization block
 * @return An [FSM] instance if the graph was successfully created
 * @throws IllegalStateException if initial state is missing, or no states are defined
 * @throws IllegalStateException if some [Transition] targets an undefined [StateId]
 * @throws IllegalArgumentException if required parameters for each definition are missing, or defined more than once
 */
fun <Context : FSMContext, Input> kfsm(kFSMBuilder: FSM.Builder<Context, Input>.() -> Unit) = KFSM(kFSMBuilder)