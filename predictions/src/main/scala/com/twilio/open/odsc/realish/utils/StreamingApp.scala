package com.twilio.open.odsc.realish.utils

import com.twilio.open.odsc.realish.listeners.InsightsQueryListener
import org.apache.spark.sql.SparkSession
import org.slf4j.Logger

trait StreamingApp {
  val logger: Logger
  def run(): Unit
}

trait Restartable {
  def restart(): Unit
}

trait RestartableStreamingApp extends StreamingApp with Restartable {
  val spark: SparkSession

  val streamingQueryListener: InsightsQueryListener = {
    new InsightsQueryListener(spark, restart)
  }

  def monitoredRun(): Unit = {
    run()
    monitorStreams()
  }

  /**
    * Call this from your run method after you've started one or more streaming queries
    */
  def monitorStreams(): Unit = {
    val streams = spark.streams
    streams.addListener(streamingQueryListener)
    streams.awaitAnyTermination()
  }

  /**
    * Call restart
    */
  def restart(): Unit = {
    logger.info(s"restarting the application. cleaning up old stream listener and streams")

    val streams = spark.streams
    streams.removeListener(streamingQueryListener)
    streams.active.foreach { stream =>
      logger.info(s"stream_name=${stream.name} state=active status=${stream.status} action=stop_stream")
      stream.stop()
    }
    logger.info(s"attempting to restart the application")
    monitoredRun()
  }
}
