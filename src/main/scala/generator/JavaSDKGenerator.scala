package generator

import java.io.PrintWriter
import java.io.StringWriter
import analyser.Clazz
import org.fusesource.scalate.DefaultRenderContext
import org.fusesource.scalate.TemplateEngine
import analyser.Method
import analyser.Package
import org.raml.model.Raml
import analyser.Analyser
import java.io.File
import analyser.DocType
import analyser.ReturnType

class JavaSDKGenerator extends Generator {

	/**
	 * Creates the sdk based on Raml
	 * @param raml - output from raml parser
	 * @param resourcePath - path to templates
	 * @param baseUrl - path to api
	 * @param tempDirectory - temporary directory
	 * @return whether SDK was generated
	 */
	override def generate(raml: Raml, resourcePath: String, baseUrl: String, tempDirectory: String): Boolean = {
		
		val pack: Package = Analyser.analyseRaml(raml)

		val classes: List[(String, String)] = pack.clazzes.map {
			clazz =>
				(clazz.name, generateClass(clazz, resourcePath + "/Class.ssp", clazz.methods.map {
					m => generateMethod(m, resourcePath + "/Method.ssp")
				}))
		}

		classes.foreach {
			tpl =>
				{
					val dest = new PrintWriter(new File(tempDirectory + "/" + tpl._1 + ".java"))
					dest.print(tpl._2)
					dest.flush()
				}
		}

		true
	}

	/**
	 * Generates Class string based on template for class in a language and previously generated methods.
	 * @param clazz - Clazz object representing a class for SDK
	 * @param classFile - path to class template
	 * @param methods - list of previously generated methods
	 * @return generated class in a string
	 */
	def generateClass(clazz: Clazz, classFile: String, methods: List[String]): String = {

		val templ = engine.load(classFile)

		val result = new StringWriter()
		val buffer = new PrintWriter(result)
		val context = new DefaultRenderContext("/", engine, buffer)

		context.attributes("className") = clazz.name
		context.attributes("methods") = methods
		context.attributes("docs") = clazz.docs
		context.attributes("version") = clazz.version
		context.attributes("baseUrl") = clazz.baseUrl
		context.attributes("baseOauthUrl") = clazz.oauthUrl
		templ.render(context)

		buffer.flush()
		result.toString()
	}

	/**
	 * Generates method based on method template
	 * @param method - Method object representing the sdk method
	 * @param methodFile - path to method template
	 * @return generated method in a string
	 */
	def generateMethod(method: Method, methodFile: String): String = {

		def nameChanger(name: ReturnType.Value): String = name match {
			case ReturnType.STRING => "String"
			case ReturnType.NUMBER => "Long"
			case ReturnType.NUMBER_LIST => "List<Long>"
			case ReturnType.STRING_LIST => "List<String>"
			case ReturnType.MAP => "Map<String,String>"
		}

		val templ = engine.load(methodFile)

		val result = new StringWriter()
		val buffer = new PrintWriter(result)
		val context = new DefaultRenderContext("/", engine, buffer)

		val tmp = method.query.map { tuple => tuple._1 }.toList

		val params = method.query.map {
			tpl =>
				if (tpl._1 == "body")
					(tpl._1, "JSONObject")
				else
					(tpl._1, nameChanger(tpl._2))
		}

		context.attributes("parameters") = params

		// add only description
		val docs = method.docs.filter(tpl => tpl._2._1 == DocType.DESCRIPTION).map {
			tpl =>
				{
					val attr = tpl._2
					(tpl._1, attr._2)
				}
		}
		context.attributes("docs") = docs

		context.attributes("methodName") = method.name
		context.attributes("url") = method.path
		context.attributes("rtype") = method.restType.toString()

		templ.render(context)
		buffer.flush()
		result.toString()

	}
}