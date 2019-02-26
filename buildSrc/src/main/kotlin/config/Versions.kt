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

package config

object BuildGradlePluginsVersions {
    val gradlePluginVersion = AGP.version
    // FIXME 4.2.0 fails on incremental compilation from AS 3.3.1
    const val googleServicesGradlePluginVersion = "4.1.0"
    const val kotlinVersion = "1.3.21"
}

object LibVersions {
    const val daggerVersion = "2.22.1"

    // Waiting for merge of https://github.com/JakeWharton/butterknife/pull/1445 to remove build warning
    const val butterKnifeVersion = "10.1.0"

    const val threetenAbpVersion = "1.1.0"

    const val kotlinCoroutinesCore = "1.2.1"
}

object AndroidXVersions {
    object ArchComponent {
        const val androidXArchCoreVersion = "2.0.0"
        const val androidXArchLifecycleVersion = "2.0.0"
        const val androidXArchLiveDataVersion = "2.0.0"
        const val androidXArchViewModelVersion = "2.0.0"
        const val androidXArchRoomVersion = "2.0.0"
        const val androidXArchPagingVersion = "2.0.0"
    }

    const val androidXCoreVersion = "1.0.1"
    const val androidXCoreKtxVersion = "1.0.0"
    const val androidXFragmentKtxVersion = "1.0.0"
    const val androidXAppCompatVersion = "1.0.2"
    const val androidXFragmentVersion = "1.0.0"
    const val androidXMaterialVersion = "1.1.0-alpha05"

    const val androidXCardViewVersion = "1.0.1"
    const val androidXConstraintLayoutVersion = "1.1.3"
    const val androidXRecyclerViewVersion = "1.0.0"
    const val androidXBrowserVersion = "1.0.0"
    const val androidXMediaVersion = "1.0.0"
    const val androidXAnnotationVersion = "1.0.0"
    const val androidXPreferenceVersion = "1.0.0"
    const val androidXMultidexVersion = "2.0.1"
    const val androidXGridLayoutVersion = "1.0.0"
    const val androidXDrawerLayoutVersion = "1.0.0"
}

object TestingVersions {
    const val androidXTestEspressoVersion = "3.1.1"
    const val androidXTestUiAutomatorVersion = "2.2.0"
    const val androidXTestOrchestratorVersion = "1.1.1"

    const val testJUnitVersion = "4.13-beta-2"
    const val testRobolectricVersion = "4.2"
    const val testGoogleTruthVersion = "0.43"
}