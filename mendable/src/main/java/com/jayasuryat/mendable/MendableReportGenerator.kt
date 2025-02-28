/*
 * Copyright 2023 Jaya Surya Thotapalli
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
package com.jayasuryat.mendable

import com.jayasuryat.mendable.MendableReportGeneratorRequest.ExportType
import com.jayasuryat.mendable.MendableReportGeneratorRequest.IncludeModules
import com.jayasuryat.mendable.export.html.MendableHtmlPage
import com.jayasuryat.mendable.export.json.jsonExportContent
import com.jayasuryat.mendable.metricsfile.ComposeCompilerMetricsFile.ComposableSignaturesReportFile
import com.jayasuryat.mendable.model.ComposeCompilerMetricsExportModel
import com.jayasuryat.mendable.model.ComposeCompilerMetricsExportModel.ModuleDetails
import com.jayasuryat.mendable.model.ComposeCompilerMetricsExportModel.Overview
import com.jayasuryat.mendable.parser.getComposableSignaturesReportFileParser
import com.jayasuryat.mendable.parser.model.ComposableSignaturesReport
import com.jayasuryat.mendable.parser.model.ComposableSignaturesReport.ComposableDetails
import com.jayasuryat.mendable.scanner.scanForComposableSignaturesReportFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.math.roundToInt

public class MendableReportGenerator(
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // TODO : Can handle errors with an either monad here.

    public suspend fun generate(
        request: MendableReportGeneratorRequest,
        progress: (progress: Progress) -> Unit = {},
    ): Progress.Result {

        return try {
            unsafeGenerator(
                request = request,
                progress = progress
            )
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            val error = Progress.Error(throwable = ex)
            progress(error)
            error
        }
    }

    private suspend fun unsafeGenerator(
        request: MendableReportGeneratorRequest,
        progress: (progress: Progress) -> Unit,
    ): Progress.Result {

        request.validate()

        progress(Progress.Initiated)

        val metricsFiles: List<ComposableSignaturesReportFile> = withContext(ioDispatcher) {
            scanForComposableSignaturesReportFiles(
                directory = request.scanPath,
                scanRecursively = request.scanRecursively,
            )
        }

        if (metricsFiles.isEmpty()) {
            val result = Progress.NoMetricsFilesFound
            progress(result)
            return result
        }

        progress(Progress.MetricsFilesFound(files = metricsFiles))

        val parsed: List<ComposableSignaturesReport> = withContext(computationDispatcher) {
            metricsFiles.parseMetrics()
        }

        progress(
            Progress.MetricsFilesParsed(
                parsedSuccessfully = parsed.size,
                failedToParse = metricsFiles.size - parsed.size,
            )
        )

        val meta = withContext(computationDispatcher) {
            parsed.toExportModel(includeModules = request.includeModules)
                .getExportContent(type = request.exportType)
        }

        val saveDir = withContext(ioDispatcher) {
            meta.save(
                outputName = request.outputFileName,
                outputDirectory = request.outputPath,
            )
        }

        val successResult = Progress.SuccessfullyCompleted(
            outputPath = saveDir,
            exportType = request.exportType,
        )

        progress(successResult)
        return successResult
    }

    private fun MendableReportGeneratorRequest.validate() {

        require(scanPath.isNotEmpty()) { "scanPath cannot be empty" }
        val input = File(scanPath)
        require(input.exists()) { "$scanPath does not exist" }
        require(input.isDirectory) { "$scanPath is not a directory" }

        require(outputPath.isNotEmpty()) { "outputPath cannot be empty" }
        val output = File(outputPath)
        require(output.exists()) { "$output does not exist" }
        require(output.isDirectory) { "$output is not a directory" }

        require(outputFileName.isNotEmpty()) { "outputFileName cannot be empty" }
    }

    private fun List<ComposableSignaturesReportFile>.parseMetrics(): List<ComposableSignaturesReport> {
        val parser = getComposableSignaturesReportFileParser()
        return this.mapNotNull { file ->
            runCatching { parser.parse(file) }.getOrNull()
        }
    }

    private fun List<ComposableSignaturesReport>.toExportModel(
        includeModules: IncludeModules,
    ): ComposeCompilerMetricsExportModel {

        fun List<ComposableSignaturesReport>.filterWithWarnings(): List<ComposableSignaturesReport> {
            return this.filter { report ->
                report.composables.any { composable -> composable.isRestartable && !composable.isSkippable }
            }
        }

        fun List<ComposableSignaturesReport>.toOverview(): Overview {

            val allComposables: List<ComposableDetails> = this.flatMap { report -> report.composables }
            val restartable = allComposables.filter { composable -> composable.isRestartable }
            val skippable = restartable.filter { composable -> composable.isSkippable }

            val percentage: Int = if (restartable.isEmpty()) 100
            else ((skippable.count() * 100f) / restartable.count()).roundToInt()

            return Overview(
                totalComposables = allComposables.size,
                restartableComposables = restartable.size,
                skippableComposables = skippable.size,
                skippablePercentage = percentage,
            )
        }

        fun ComposableSignaturesReport.toModuleDetails(): ModuleDetails {
            return ModuleDetails(
                overview = listOf(this).toOverview(),
                report = this,
            )
        }

        val parsed: List<ComposableSignaturesReport> = this
        val reporting: List<ComposableSignaturesReport> = when (includeModules) {
            IncludeModules.ALL -> parsed
            IncludeModules.WITH_WARNINGS -> parsed.filterWithWarnings()
        }
        val mapped = reporting.map { report -> report.toModuleDetails() }

        return ComposeCompilerMetricsExportModel(
            modules = mapped,
            overview = parsed.toOverview(),
            totalModulesScanned = parsed.size,
            totalModulesReported = reporting.size,
        )
    }

    private fun ComposeCompilerMetricsExportModel.getExportContent(
        type: ExportType,
    ): ExportMeta {

        val content: String = when (type) {
            ExportType.HTML -> MendableHtmlPage(report = this)
            ExportType.JSON -> this.jsonExportContent()
        }

        return ExportMeta(
            content = content,
            extension = when (type) {
                ExportType.HTML -> "html"
                ExportType.JSON -> "json"
            }
        )
    }

    private data class ExportMeta(
        val content: String,
        val extension: String,
    )

    private fun ExportMeta.save(
        outputName: String,
        outputDirectory: String,
    ): String {

        val directory = File(Paths.get(outputDirectory).absolutePathString())
        val file = File("${directory.absolutePath}/$outputName.$extension")
        file.writeText(content)

        return file.absolutePath
    }

    public sealed interface Progress {

        public sealed interface Result

        public object Initiated : Progress {
            override fun toString(): String = "Progress.Initiated"
        }

        public class MetricsFilesFound(
            public val files: List<ComposableSignaturesReportFile>,
        ) : Progress {
            override fun toString(): String = "Progress.MetricsFilesFound(files=$files)"
        }

        public class MetricsFilesParsed(
            public val parsedSuccessfully: Int,
            public val failedToParse: Int,
        ) : Progress {
            // TODO: Need to present what file was not parsed and what was the reason
            override fun toString(): String =
                "Progress.MetricsFilesParsed(parsedSuccessfully=$parsedSuccessfully, failedToParse=$failedToParse)"
        }

        public class SuccessfullyCompleted(
            public val outputPath: String,
            public val exportType: ExportType,
        ) : Progress, Result {
            override fun toString(): String =
                "Progress.SuccessfullyCompleted(outputDir=$outputPath, exportType=$exportType)"
        }

        public object NoMetricsFilesFound : Progress, Result {
            override fun toString(): String = "Progress.NoMetricsFilesFound"
        }

        public class Error(
            public val throwable: Throwable,
        ) : Progress, Result {

            override fun toString(): String = "Progress.Error(throwable=$throwable)"
        }
    }
}
