# KFSM

A simple Finite state machine library in Kotlin.

## Overview

The FSM structure must be specified during construction and is fixed after its creations.
Whenever starting the FSM, a new [FSMRunner] instance is created, the FSM is initialized to its initial state.
[FSMRunner] can then be fed input by calling [FSMRunner.feedAsync]; the FSM will transition to a new state based on the current state and its available transitions.
[FSMRunner] can also be used to get the [FSMRunner.currentState], the [FSMRunner.currentContext] or list [FSMRunner.availableTransitions] from [FSMRunner.currentState].

## Installation

### Android Gradle

#### Groovy

```groovy
repositories {
    maven {
        url  "https://dl.bintray.com/yochiro/android"
    }
}

dependencies {
  implementation 'org.ymkm.android:kfsm:1.0.0'
}
```

#### Kotlin DSL

```kotlin
repositories {
    maven {
        url = URL("https://dl.bintray.com/yochiro/android")
    }
}

dependencies {
  implementation("org.ymkm.android:kfsm:1.0.0")
}
```

## FSM Context

A [FSMContext] is an arbitrary object that is passed when [FSM.start]ing the FSM that defines properties to be shared by the [FSMRunner] during
its lifecycle; the context instance can be queried, updated in actions triggered on [State.Builder.enterWithState] or [State.Builder.exitWithState], on actions
during [Transition.Builder.actionWithState] to maintain the state of the current running instance.

## Creation

To create a new FSM, use the [kfsm] function; its argument is a block that defines all states (themselves defining transitions) available within the FSM.
Upon creating the FSM, static checks are performed to ensure that no [StateId.id] are duplicated and that all [Transition] outgoing from all states target a [StateId]
defined in the fsm block. An [FSM] must always define an [FSM.Builder.initialState]. This state is the one which the [FSMRunner]
will enter when the FSM is started.

## Usage

### Context data model
Define an [FSMContext] subclass to use. It may be an empty object if not required.

```kotlin
data class MyContext(var count: Int) : FSMContext
```

### FSM Creation

Create a new FSM definition by using the code below :

```kotlin
     val myfsm = kfsm<MyContext> {

         // ... fsm definition ...

     }
```

### Specify FSM states

 _An [kfsm] requires at least an initial state. It must have at least 1 state (including the initial state)_
 
 The [kfsm] function takes a block where the states, including the initial state, can be defined. Each state/initial state requires at least an [State.Builder.id].

 There are two ways to define a state/initial state inside an FSM.

 - Use the [FSM.Builder.state] function defined at the block level
 E.g.

 ```kotlin
 kfsm<MyContext> {
     state {
         id = 1
         label = "State label" // Optional
     }
 }
 ```
 - Use the string extension [FSM.Builder.state] defined at the block level. The string defines the label for the state.
 E.g.

 ```kotlin
 kfsm<MyContext> {
     "State label" state {
         id = 1
     }
 }
 ```

 _To specify the initial state, use resp. the [FSM.Builder.initialState] or [FSM.Builder.initialState] functions instead._

### Specify state enter/exit actions

 Each state can optionally be setup with an enter and/or exit action. The enter action is triggered when a transition reaches the state; the exit action is triggered when leaving the state due to a transition.
 The initial state enter action will be triggered as soon as the FSM is started.

 There are two variants available, depending on whether the [FSMRunner.currentContext], [FSMRunner.currentState] or [FSMRunner.availableTransitions] are required for processing.

 - [State.Builder.enter] | [State.Builder.exit] -> A no arg function block to specify an action without any dependency on [FSMRunner]
 - [State.Builder.enterWithState] | [State.Builder.exitWithState] -> A 1-arg function block to specify an action which gets passed the [FSMRunner] for the current running instance.

### Specify outgoing transitions for each state

 Each non-final state will have at least an outgoing transition to another state, while final state (sinks) only have ingoing transitions.

 To define transitions, use the [KState.Builder.transitions] block :

```kotlin
kfsm<MyContext> {
    state {
        id = 1
        label = "State label"
        transitions {
            // ... define outgoing transitions here ...
        }
    }
}
```

Inside the transitions block, there are 3 ways to define a transition :

- For simple transitions (no action, no conditions), use the [KTransition.ListBuilder.transition] that takes 2 mandatory arguments and an optional label :

```kotlin
transitions {
    transition(3) // target state ID 3, without a transition label
    transition(4, "Target State 4") // target state ID 4, with a transition label
}
```

- Use the variant [KTransition.ListBuilder.transition] that can take an optional action block and/or condition :

```kotlin
transitions {
    transition {
        to = 2 // Target state ID when using this transition
        label = "Transition label" // Optional
        // simple no-arg action 
        action {
            // ....
        }
        // or action with arguments
        actionWithState { runner ->
            // ....
        }
        condition { input, context ->
            // ....
        }
    }
}
```

 - Use the string extension [Transition.ListBuilder.transition] defined at the block level. The string defines the label for the transition.
 E.g.

 ```kotlin
 transitions {
     "Transition label" transition {
         to = 2
        // simple no-arg action 
        action {
            // ....
        }
        // or action with arguments
        actionWithState { runner ->
            // ....
        }
        condition { input, context ->
            // ....
        }
     }
 }
 ```

### Specify transition actions

 Each transition can optionally be setup with an action to be performed when the transition is taken.

 There are two variants available, depending on whether the [FSMRunner.currentContext], [FSMRunner.currentState] or [FSMRunner.availableTransitions] are required for processing.

 - [Transition.Builder.action] -> A no arg function block to specify an action without any dependency on [FSMRunner]
 - [Transition.Builder.actionWithState] -> A 2-arg function block to specify an action which gets passed the [Transition] currently in progress, and the [FSMRunner] for the current running instance.

### Specify transition condition

A transition can be bound to an optional predicate function that is checked for each input given; if the predicate returns true, then the transition satisfies the conditions required to change state to the specified target.

If more than one transition is defined on the current state while a new input is given, exactly 1 transition predicate should return true. Failing to do so will result in a runtime error.

Define a transition predicate like follow (in this case, the input type is a String) :
E.g.

```kotlin
transitions {
    "Transition if starts with letter a" transition {
        to = 2
        condition { input, runner ->
            return input.startsWith("a")
        }
    }
    "Transition if starts with letter b" transition {
        to = 3
        condition { input, runner ->
            return input.startsWith("b")
        }
    }
    // ... etc ...
}

// ...

runner.transition("a string") // Will use transition 1 and go to state 2
```

## Running

 [FSM] instances only define the structure of the graph, and are stateless. To create a new running instance, use the [FSM.start] function.
 You must pass in the initial [FSMContext] value, as well as an optional [FSMRunner.Observer] object. The latter will receive events as the FSM transitions from one state to another.
 The return value is a new instance of [FSMRunner] that can be used to transition, or query the current state of the running instance.
 Multiple call to [FSM.start] are allowed, and each will yield a new instance running.

### Transition

 Use the [FSMRunner.feedAsync] function to feed in some new input that should internally yield in a transition to another state. An error will be raised if the input specified cannot reduce to a single transition at current state.
 If a single transition exists that qualifies for given input, the following call sequence will be performed, in that order :

 - [State.Builder.exit] | [State.Builder.exitWithState], if defined on [FSMRunner.currentState]
 - [Transition.Builder.action] | [Transition.Builder.actionWithState], if defined for the transition specified.
 - [State.Builder.enter] | [State.Builder.enterWithState], if defined on the [Transition.toStateId]

 When reaching such final states, the [FSMRunner] ends and any further call to [FSMRunner.feedAsync] will result in a noop. The final state will be available through [FSMRunner.currentState], while the last state of [FSMContext] will be available through [FSMRunner.currentContext].

## Example

Following the example given on the [Wikipedia page](https://en.wikipedia.org/wiki/File:DFAexample.svg) for a FSM that checks if given integer in binary has an even number of zeros

```kotlin
data class CheckParity(var hasEvenNumberOfZeros: Boolean = false) : FSMContext

val checkNumberParity = kfsm<MyContext, Int> {
    "S1" initialState {
        id = 1
        enterWithState { runner ->
            runner.currentContext.hasEvenNumberOfZeros = true
        }
        transitions {
            "Got 0" transition {
                to = 2
                condition { input, _ ->
                    input == 0
                }
            }
            "Got 1" transition {
                to = 1
                condition { input, _ ->
                    input == 1
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
                condition { input, _ ->
                    input == 0
                }
            }
            "Got 1" transition {
                to = 2
                condition { input, _ ->
                    input == 1
                }
            }
        }
    }
}
```

## License

MIT License

Copyright (c) 2019 Yoann Mikami

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
