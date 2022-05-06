package org.gbif.pipelines.common.beam.utils;

import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.DIRECTORY_NAME;

import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.Path;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.common.PipelinesVariables;
import org.gbif.pipelines.common.beam.options.BasePipelineOptions;

import java.util.Locale;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PathBuilder {

  /** Build a {@link Path} from an array of string values using path separator. */
  public static Path buildPath(String... values) {
    return new Path(String.join(Path.SEPARATOR, values));
  }

  /**
   * Uses pattern for path - "{targetPath}/{datasetId}/{attempt}/{name}"
   *
   * @return string path
   */
  public static String buildDatasetAttemptPath(
      BasePipelineOptions options, String name, boolean isInput) {
    return String.join(
        Path.SEPARATOR,
        isInput ? options.getInputPath() : options.getTargetPath(),
        options.getDatasetId() == null || "all".equalsIgnoreCase(options.getDatasetId())
            ? "*"
            : options.getDatasetId(),
        options.getAttempt().toString(),
        name.toLowerCase());
  }

  /**
   * Uses pattern for path -
   * "{targetPath}/{datasetId}/{attempt}/interpreted/{name}/interpret-{uniqueId}"
   *
   * @return string path to interpretation
   */
  public static String buildPathInterpretUsingTargetPath(
      BasePipelineOptions options, String name, String uniqueId) {
    return buildPathInterpretUsingTargetPath(
            options,
            options.getDwcCore(),
            name,
            PipelinesVariables.Pipeline.Interpretation.FILE_NAME + uniqueId);
  }


  /**
   * Uses pattern for path -
   * "{targetPath}/{datasetId}/{attempt}/interpreted/{coreTerm.toLowercase}/{name}/interpret-{uniqueId}"
   *  The core term path is empty if it is an occurrence.
   * @return string path to interpretation
   */
  public static String buildPathInterpretUsingTargetPath(
    BasePipelineOptions options, DwcTerm coreTerm, String name, String uniqueId) {
    return buildPath(
      buildDatasetAttemptPath(options, DIRECTORY_NAME, false),
      DwcTerm.Event != coreTerm?  "" : DwcTerm.Event.name().toLowerCase(Locale.ROOT),
      name,
      PipelinesVariables.Pipeline.Interpretation.FILE_NAME + uniqueId)
      .toString();
  }

  /**
   * Uses pattern for path -
   * "{targetPath}/{datasetId}/{attempt}/interpreted/{name}/interpret-{uniqueId}"
   *
   * @return string path to interpretation
   */
  public static String buildPathInterpretUsingInputPath(
      BasePipelineOptions options, String name, String uniqueId) {
    return buildPath(
            buildDatasetAttemptPath(options, DIRECTORY_NAME, true),
            name,
            PipelinesVariables.Pipeline.Interpretation.FILE_NAME + uniqueId)
        .toString();
  }

  /**
   * Builds the target base path of a hdfs view.
   *
   * @param options options pipeline options
   * @return path to the directory where the occurrence hdfs view is stored
   */
  public static String buildFilePathViewUsingInputPath(
      BasePipelineOptions options, String type, String uniqueId) {
    return buildPath(
            buildDatasetAttemptPath(options, DIRECTORY_NAME, true), type.toLowerCase(), uniqueId)
        .toString();
  }

  /**
   * Gets temporary directory from options or returns default value.
   *
   * @return path to a temporary directory.
   */
  public static String getTempDir(BasePipelineOptions options) {
    return Strings.isNullOrEmpty(options.getTempLocation())
        ? buildPath(options.getTargetPath(), "tmp").toString()
        : options.getTempLocation();
  }
}
