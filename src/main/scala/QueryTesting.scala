import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._

import java.io._
import scala.io.StdIn._

object QueryTesting {
  def main(args: Array[String]): Unit = {
    println("Hello World!")

    /*
     * Logger: This is the central interface in the log4j package. Most logging operations, except configuration, are done through this interface.
     * getLogger: Returns a Logger with the name of the calling class
     * Level:  used for identifying the severity of an event.
     * ERROR: An error in the application, possibly recoverable.
     */
    Logger.getLogger("org").setLevel(Level.ERROR)

    //for reading files in spark-warehouse
    //System.setProperty("hadoop.home.dir", "C:\\hadoop")


    val spark =
      SparkSession
        .builder
        .appName("Hello Spark App") //Sets a name for the application
        //.master("local")
        .config("spark.master", "local")  //Sets configuration option - Sets the Spark master URL to connect to, such as "local" to run locally
        .config("spark.eventLog.enabled", false)  //Whether to log Spark events, useful for reconstructing the Web UI after the application has finished.
        .enableHiveSupport()  //Enables Hive support, including connectivity to a persistent Hive metastore
        .getOrCreate()  //Gets an existing SparkSession or, if there is no existing one, creates a new one based on the options set in this builder.


    Logger.getLogger("org").setLevel(Level.ERROR)
    spark.sparkContext.setLogLevel("ERROR")
    println("Hello Spark")

    //read data from csv
    val CovidData = spark.read.format("csv").option("inferSchema", true)
      .options(Map("header" -> "true", "delimiter" -> ","))
      .load("src/main/source/covid_19_data.csv")
      //.load("C:/Input/P2/covid_19_data.csv")
      .toDF("ID", "Obsv_Date", "Province_State", "Country_Region", "Updated", "Confirmed", "Deaths", "Recovered")

    //create table structure and cast column types
    val covidDF = CovidData.withColumn("ID", col("ID").cast("Integer"))
      .withColumn("Updated", col("Updated").cast("Timestamp"))
      .withColumn("Confirmed", col("Confirmed").cast("Double"))
      .withColumn("Deaths", col("Deaths").cast("Double"))
      .withColumn("Recovered", col("Recovered").cast("Double"))
      .withColumn("Obsv_Date", to_date(col("Obsv_Date"), "MM/dd/yyyy"))
      .persist()

    //sort and filter table
    //removes rows with null values in province_state and updated
    val covidUSA = covidDF.filter(covidDF("Country_Region")==="US")
    val cleanUSA = covidUSA.filter(covidDF("Province_State").isNotNull && covidDF("Updated").isNotNull)
    val covidChina = covidDF.filter(covidDF("Country_Region")==="Mainland China")
    val covidDFNN = covidDF.filter(covidDF("Province_State").isNotNull && covidDF("Updated").isNotNull)
    val covidDF2 = covidDFNN.filter("Province_State NOT IN ('Unknown')")

    //write table to spark-warehouse
    //not sure that we need this, it
    //covidDF2.write.saveAsTable("covidComplete")

    //attempt to use overwrite mode, but returns "covidComplete already exists"
    //covidDF2.write.mode("overwrite").saveAsTable("covidComplete")

    //requires no other code to run tables in spark-warehouse
    //spark.sql("select * from covidComplete").show()

    //create view, load table, query table
    covidDF2.createOrReplaceTempView("CovidDF2")
    val t1 = spark.table("CovidDF2").cache()

    covidUSA.createOrReplaceTempView("covidUSA")
    val t2 = spark.table("covidUSA").cache()
    val query1 = "select * from CovidDF2"
    val query2 = "select COUNT(DISTINCT country_region) from CovidDF2"
    val query3 = "select DISTINCT country_region from CovidDF2 ORDER BY country_region"
    val query4 = "select country_region, SUM(Deaths) as totalDeaths from CovidDF2 GROUP BY country_region ORDER BY totalDeaths DESC"

    cleanUSA.createOrReplaceTempView("cleanUSA")
    val t3 = spark.table("cleanUSA").cache()
    //shows table preview
    //t1.sqlContext.sql(query1).show()
    //there are 31 country_region values
    //t1.sqlContext.sql(query2).show(40)
    //shows existing country_region values
    //t1.sqlContext.sql(query3).show(40)
    //shows total deaths per country_region
    //t1.sqlContext.sql(query4).show(40)
    //shows total covid deaths overtime
    def runQuery() {
      var command = ""
      while (command != "3") {
        var query = readLine("Enter option [1]query, [2]saveToJSON, [3]stop: ")
        if (query == "1") {
          try {
            val query2 = readLine("Enter query: ")
            //t2.sqlContext.sql(query).show(40)
            t1.sqlContext.sql(query2).show(40)
          }
          catch {
            case a: Exception => println("Incorrect input")
          }
        }
        else if (query == "2") {
          try {
            val query3 = readLine("Enter query to save to JSON: ")
            val filename = readLine("Enter file name(DO NOT APPEND .json): ")
            t1.sqlContext.sql(query3)
              .toDF //cast to DataFrame type
              .coalesce(1) //combine into 1 partition
              .write
              .mode(SaveMode.Overwrite) //overwrite existing file
              .json(s"json_export/$filename.json")
          }
          catch {
            case a: Exception => println("Incorrect input")
          }
        }
        else if (query == "3") {
          spark.stop()
          command = "3"
        }
      }
    }
    runQuery()

    

  }
}