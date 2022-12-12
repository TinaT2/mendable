<div align="center">

  <a>
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://user-images.githubusercontent.com/37530409/205920274-d4cd2c4e-92d9-40d8-ac0f-39e8374600d6.svg" height="200">
      <img alt="Dowel banner" src="https://user-images.githubusercontent.com/37530409/205920279-1c22ea9e-1f81-45d9-9994-01785e9ab473.svg" height="200">
    </picture>
  </a>

  <h1>A <code>CLI</code> tool to convert <code>multi-module</code></br><code>Jetpack Compose</code> compiler <code>metrics</code></br>into beautiful <code>HTML</code> reports</h1>

  <a href="https://github.com/jayasuryat/mendable/releases/download/0.5.0/mendable.jar"><img alt="Download Mendable" src="https://img.shields.io/badge/Mendable.jar-0.5.0-%2306090E?style=for-the-badge&logo=jetpackcompose"/></a>
  
  <p align="center">
    <a href="https://opensource.org/licenses/Apache-2.0"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"/></a>
    <a href="https://androidweekly.net/issues/issue-548"><img alt="AndroidWeekly" src="https://img.shields.io/badge/AndroidWeekly-%23548-2299cc.svg?style=flat&logo=android"/></a>
    <a href="https://mailchi.mp/kotlinweekly/kotlin-weekly-332"><img alt="KotlinWeekly" src="https://img.shields.io/badge/KotlinWeekly-%23332-7549b5.svg?style=flat&logo=kotlin"/></a>
        <a href="https://github.com/jayasuryat/mendable/actions/workflows/main.yml"><img alt="CI" src="https://github.com/jayasuryat/mendable/actions/workflows/main.yml/badge.svg"/></a>
  </p>

</div>

<details>
  <summary><h2>1. What are Jetpack Compose compiler metrics?</h2></summary>

The [`Compose`](https://developer.android.com/jetpack/compose) `Compiler plugin` can generate `reports/metrics` around
certain Compose-specific concepts that can be useful in understanding what is happening with some of the `Compose` code
at a fine-grained level.

It can output various performance-related `metrics` at build time, allowing us to peek behind the curtains and see where
any potential `performance issues` are.

Read more [here](https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md)

</details>

## 2. Why Mendable?

Although, the `metrics` generated by the `Compose Compiler` are in pseudo-Kotlin style function signatures, which are
reasonably readable. But, over time these files get big and unwieldy to work with, and checking
each-and-every `composable` in those `txt files` becomes cumbersome and clumsy. Also, these reports are spread across
different `files` for different `modules`.

**That is where Mendable comes in and takes care of generating `HTML reports` for `Compose` `compiler metrics`, which
are much easier to work with. And Mendable only presents `composable` methods which need your attention, and highlights
the issue-causing part with appropriate colors. It filters out the rest of the `composables` which are
non-problematic.**

Mendable also takes reports of `multiple modules` and merges them into different sections of a single
beautiful `HTML page` to reduce going back and forth while working.

> **Note** : Mendable only generates HTML report for the composables metrics. In other words, it only targets
> the '<module>-composables.txt` files.

<details>
  <summary><h2>3. Gradle setup - for generating Compose compiler metrics</h2></summary>

Add the following lines to your **root project's** `build.gradle` file. This will direct the Compose compiler to
generate metrics and save all of them into the **root project's** `build folder` (for all of the `modules`).

<details open>
  <summary><code>Groovy</code></summary>

``` groovy
subprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        kotlinOptions {
            // Trigger this with:
            // ./gradlew assembleRelease -PenableMultiModuleComposeReports=true --rerun-tasks
            if (project.findProperty("enableMultiModuleComposeReports") == "true") {
                freeCompilerArgs += ["-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" + rootProject.buildDir.absolutePath + "/compose_metrics/"]
                freeCompilerArgs += ["-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" + rootProject.buildDir.absolutePath + "/compose_metrics/"]
            }
        }
    }
}
```

</details>

<details>
  <summary><code>Kotlin scipt</code></summary>

```kotlin
allprojects {
    tasks.withType(org.jetbrains.kotlin.gradle.dsl.KotlinCompile::class.java).configureEach {
        kotlinOptions {
            // Trigger this with:
            // ./gradlew assembleRelease -PenableMultiModuleComposeReports=true --rerun-tasks
            if (project.findProperty("enableMultiModuleComposeReports") == "true") {
                freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" + rootProject.buildDir.absolutePath + "/compose_metrics/")
                freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" + rootProject.buildDir.absolutePath + "/compose_metrics/")
            }
        }
    }
}
```

</details>

With the above setup, you can generate Compose compiler metrics by executing the following `command` in the `terminal`
window.

```
./gradlew assembleRelease -PenableMultiModuleComposeReports=true --rerun-tasks
```

</details>

## 4. How do I use Mendable?

> **Note** : The following steps assume that you have a similar `gradle` setup as to the one described
> in [step 3](https://github.com/jayasuryat/mendable#3-gradle-setup---for-generating-compose-compiler-metrics).

It is very straightforward. Download and execute the `jar` file while **pointing it to the appropriate directory** which
contains all the Compose compiler-generated metrics files.

Mendable will take care of the rest. It will figure out metrics files of individual `modules`, `parse` them, `compute`
and `generate` a beautiful `HTML report` for you.

### 4.1 ✨ Generate HTML report with a single command ✨

[Download](https://github.com/jayasuryat/mendable/releases/download/0.5.0/mendable.jar) and place the `jar` file in the same folder which contains all the generated Compose compiler metrics (
should be YourProject/build/compose_metrics). And then execute the `jar` file with the following `command`.

```
java -jar mendable.jar
```

After executing this `command` there should be `index.html` file generated in the same folder, which will contain the
combined metrics of all the modules.

<details>
    <summary><h3>4.2 Generate HTML report by specifying paths manually</h3></summary>

While the above method is the easiest, and should work fine for most of the use cases, Mendable also supports reading
and writing files to custom locations. The following are the supported options via `CLI arguments`.

```
java -jar mendable.jar
    --composablesReportsPath, -i  [Default value : <Current working dir>] -> Path to the directory containing all of the composables.txt files
    --htmlOutputPath, -o          [Default value : <Current working dir>] -> HTML output directory
    --outputName, -oName          [Default value : "index"]               -> Name of the output HTML file
    --help, -h                                                            -> Usage info
```

For example :

```
java -jar mendable.jar
    -i /Users/username/Desktop/Your-project/build/compose_metrics \
    -o /Users/username/Desktop/Reports \
    -oName Your-project-metrics \
```

For the above command, files will be `read` from '/Users/username/Desktop/Your-project/build/compose_metrics' and
the `output` file will be `saved` at '/Users/username/Desktop/Reports' and that file will be `named` '
Your-project-metrics.html'.
</details>

<div align="center">
  <br>
  <h3>And your <code>HTML</code> report should look something like this</h3>
</div>

![Mendable sample screenshot](https://user-images.githubusercontent.com/37530409/206190055-33332a9c-f953-40d0-82a7-8d8df5d796f0.png)

---

## Other things to know
* Clicking on the composable's name in the `HTML` report will copy that name to the `clipboard`

* You can build an executable `jar` yourself by executing the following command in the root of the project
```
./gradlew mendable:clean mendable:jar
```

## Contributions
Contributions are super welcome! See [Contributing Guidelines](https://github.com/jayasuryat/mendable/blob/main/CONTRIBUTING.md).

## Discussions
Have any questions or queries, or want to discuss anything? Feel free and [start a discussion](https://github.com/jayasuryat/mendable/discussions).

## Acknowledgements
Huge thanks to [Shreyas Patil](https://github.com/PatilShreyas). This repo has been inspired by his version
of [compose-report-to-html](https://github.com/PatilShreyas/compose-report-to-html) CLI tool.

## License

```
 Copyright 2022 Jaya Surya Thotapalli

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
