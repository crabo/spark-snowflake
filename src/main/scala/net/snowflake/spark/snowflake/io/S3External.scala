package net.snowflake.spark.snowflake.io

import net.snowflake.spark.snowflake.{CloudCredentialsUtils, JDBCWrapper, Utils}
import net.snowflake.spark.snowflake.Parameters.MergedParameters
import net.snowflake.spark.snowflake.io.SupportedFormat.SupportedFormat
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.slf4j.{Logger, LoggerFactory}


class S3External(
                  val sqlContext: SQLContext,
                  val params: MergedParameters,
                  val sql: String,
                  val jdbcWrapper: JDBCWrapper,
                  val format: SupportedFormat = SupportedFormat.CSV
                ) extends DataUnloader {
  override val log: Logger = LoggerFactory.getLogger(getClass)


  def getRDD(): RDD[String] = {
    val tempDir = params.createPerQueryTempDir()

    val numRows = setup(
      sql = buildUnloadStmt(
        query = sql,
        location = Utils.fixUrlForCopyCommand(tempDir),
        compression = if (params.sfCompress) "gzip" else "none",
        credentialsString = Some(
          CloudCredentialsUtils.getSnowflakeCredentialsString(sqlContext,
            params))),

      conn = jdbcWrapper.getConnector(params))

    if (numRows == 0) {
      // For no records, create an empty RDD
      sqlContext.sparkContext.emptyRDD[String]
    } else {
      format match {
        case SupportedFormat.CSV =>
          sqlContext.sparkContext.newAPIHadoopFile(
            tempDir,
            classOf[S3CSVInputFormat],
            classOf[java.lang.Long],
            classOf[String]
          ).map(_._2)
        case SupportedFormat.JSON =>
          sqlContext.sparkContext.emptyRDD[String]
      }
    }
  }

}

