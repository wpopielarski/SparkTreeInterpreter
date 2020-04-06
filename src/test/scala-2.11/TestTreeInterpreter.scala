import com.holdenkarau.spark.testing.LocalSparkContext
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Suite}
import org.apache.spark.ml.treeinterpreter.Interp
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.{functions => F}
import org.apache.spark.ml.regression.RandomForestRegressor
import org.apache.spark.ml.regression.RandomForestRegressionModel
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.classification.RandomForestClassifier
import org.apache.spark.ml.classification.RandomForestClassificationModel

class InterpTest extends FunSuite with SharedSparkSession {
  test("Random Forest Regression Test") {
    val ss = spark
    import ss.implicits._
    val currentDir = System.getProperty("user.dir")
    val replacementPath = s"$currentDir/src/test/resources/bostonData.data"
    val data = spark
      .read
      .option("header", true)
      .csv(replacementPath)
      .select(('A' to 'N').map {c => F.col(c.toString).cast("double")}:_*)

    val assembler = new VectorAssembler()
      .setInputCols(('A' until 'N').map{c => c.toString}.toArray)
      .setOutputCol("features")
    
    val featuredData = assembler
      .transform(data)
      .select($"features", $"N".as("label"))

    featuredData.printSchema()
    featuredData.show(5)
    val splits = featuredData.randomSplit(Array(0.2, 0.8), seed = 5)
    val (trainingData, testData) = (splits(0), splits(1))

    val numTrees = 10
    val featureSubsetStrategy = "auto"
    val maxDepth = 2
    val maxBins = 32

    val classimpurity = "variance"

    val rf = new RandomForestRegressor()
      .setSeed(1234)
      .setImpurity(classimpurity)
      .setMaxBins(maxBins)
      .setMaxDepth(maxDepth)
      .setFeatureSubsetStrategy(featureSubsetStrategy)
      .setNumTrees(numTrees)
    
    val rfModel : RandomForestRegressionModel = rf.fit(trainingData)

    val interpDataset = Interp.interpretModelTf(spark, rfModel, testData)

    interpDataset.take(5).foreach(println)
    println(rfModel.featureImportances)
    println(rfModel.featureImportances)
    println(trainingData.schema("features").metadata.getMetadata("ml_attr").getMetadata("attrs"))
  }

  test("Random Forest Classifier Test") {
    val ss = spark
    import ss.implicits._
    val currentDir = System.getProperty("user.dir")
    val replacementPath = s"$currentDir/src/test/resources/bostonData.data"
    val data = spark
      .read
      .option("header", true)
      .csv(replacementPath)
      .select(('A' to 'N').map {c => F.col(c.toString).cast("double")}:_*)

    val assembler = new VectorAssembler()
      .setInputCols((Seq("A", "B", "C") ++ ('E' to 'N').map{c => c.toString}).toArray)
      .setOutputCol("features")
    
    val featuredData = assembler
      .transform(data)
      .select($"features", $"D".as("label"))

    featuredData.printSchema()
    featuredData.show(5)
    val splits = featuredData.randomSplit(Array(0.2, 0.8), seed = 5)
    val (trainingData, testData) = (splits(0), splits(1))

    val numTrees = 10
    val featureSubsetStrategy = "auto"
    val maxDepth = 2
    val maxBins = 32

    val classimpurity = "gini"

    val rf = new RandomForestClassifier()
      .setSeed(1234)
      .setImpurity(classimpurity)
      .setMaxBins(maxBins)
      .setMaxDepth(maxDepth)
      .setFeatureSubsetStrategy(featureSubsetStrategy)
      .setNumTrees(numTrees)
    
    val rfModel : RandomForestClassificationModel = rf.fit(trainingData)

    val interpDataset = Interp.interpretModelTf(spark, rfModel, testData)

    interpDataset.take(5).foreach(println)
    println(rfModel.featureImportances)
    println(trainingData.schema("features").metadata.getMetadata("ml_attr").getMetadata("attrs"))
  }
}

trait SharedSparkSession extends BeforeAndAfterAll {
  self: Suite =>

  @transient private var _ss: SparkSession = _

  implicit def spark: SparkSession = _ss

  val conf = new SparkConf().setMaster("local[*]")
    .setAppName("test")


  override def beforeAll() {
    _ss = SparkSession
          .builder
          .config(conf)
          .getOrCreate()
    super.beforeAll()
  }

  override def afterAll() {
    _ss.stop()
    _ss = null
    super.afterAll()
  }
}