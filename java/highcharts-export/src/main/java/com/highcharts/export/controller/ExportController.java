/**
 * @license Highcharts JS v2.3.3 (2012-11-02)
 *
 * (c) 20012-2014
 *
 * Author: Gert Vaartjes
 *
 * License: www.highcharts.com/license
 */
package com.highcharts.export.controller;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.highcharts.export.converter.SVGConverter;
import com.highcharts.export.converter.SVGConverterException;
import com.highcharts.export.pool.PoolException;
import com.highcharts.export.util.MimeType;

@Controller
@RequestMapping("/")
public class ExportController extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Float MAX_WIDTH = 2000.0F;
	private static final Float MAX_SCALE = 4.0F;
	protected static Logger logger = Logger.getLogger("exporter");

	/*for test*/
	@Autowired
    private ServletContext servletContext;

	/* end*/

	@Resource(name = "svgConverter")
	private SVGConverter converter;

	/* Catch All */
	@RequestMapping(method = RequestMethod.POST)
	public void exporter(
			@RequestParam(value = "svg", required = false) String svg,
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "filename", required = false) String filename,
			@RequestParam(value = "width", required = false) String width,
			@RequestParam(value = "scale", required = false) String scale,
			@RequestParam(value = "options", required = false) String options,
			@RequestParam(value = "constr", required = false) String constructor,
			@RequestParam(value = "callback", required = false) String callback,
			HttpServletResponse response, HttpServletRequest request)
			throws ServletException, IOException, InterruptedException, SVGConverterException, NoSuchElementException, PoolException, TimeoutException {

		long start1 = System.currentTimeMillis();

		MimeType mime = getMime(type);
		filename = getFilename(filename);
		Float parsedWidth = widthToFloat(width);
		Float parsedScale = scaleToFloat(scale);
		options = sanitize(options);
		String inputs;

		boolean convertSvg = false;

		// Create return JSON object
		JSONObject jobj = new JSONObject();
		
		if (options != null) {
			// create a svg file out of the options
			inputs = options;
			callback = sanitize(callback);
		} else {
			jobj.put ("status", -1);
			jobj.put ("msg", "The svg POST is not supported.");
			response.reset();
			response.setContentType ("application/json");
			response.getWriter().print(jobj);
			response.flushBuffer();
			return;
		}

		String[] inputList = inputs.split("!#!");
		String input;
		
		ByteArrayOutputStream stream = null;
		//stream = SVGCreator.getInstance().convert(input, mime, constructor, callback, parsedWidth, parsedScale);

		String fileList = "";
		String chartFilename = "";
		for (int i=0; i < inputList.length; i++) {
			input = inputList[i];
			stream = converter.convert(input, mime, constructor, callback, parsedWidth, parsedScale);
	
			if (stream == null) {
				//throw new ServletException("Error while converting");
				jobj.put ("status",-1);
				jobj.put ("msg", "Error while converting.");
				response.reset();
				response.setContentType ("application/json");
				response.getWriter().print(jobj);
				response.flushBuffer();
				return;
			}
	
			logger.debug(request.getHeader("referer") + " Total time: " + (System.currentTimeMillis() - start1));
	
			// Now Save the it to file. And return the path.
			String uuid = UUID.randomUUID().toString();
			String extension = ".png";
			if (mime.compareTo(MimeType.JPEG)==0) {
				extension = ".jpg";
			} else if (mime.compareTo(MimeType.PDF)==0) {
				extension = ".pdf";
			} else if (mime.compareTo(MimeType.PNG)==0) {
				extension = ".png";
			} else if (mime.compareTo(MimeType.SVG)==0) {
				extension = ".svg";
			}
			
			chartFilename = uuid+extension;
			//OutputStream chartFile = new FileOutputStream ("C:\\inetpub\\wwwroot\\tmp\\"+chartFilename);
			OutputStream chartFile = new FileOutputStream ("C:\\Users\\mc142\\Source\\Repos\\JourneyCompass\\website\\tmp\\"+chartFilename);
			stream.writeTo(chartFile);
			chartFile.close();
			
			if (i == inputList.length-1) {
				fileList += chartFilename;
			} else {
				fileList += chartFilename+"!#!";
			}
			stream = null;
		}		
		
		jobj.put("filenames", fileList);
		jobj.put("status", 0);
		jobj.put("msg", "success"); 
		response.reset();
		response.setCharacterEncoding("utf-8");
		response.setContentType ("application/json");
		response.setStatus(HttpStatus.OK.value());
		response.addHeader("Access-Control-Allow-Origin", "*");
		//response.setHeader("Access-Control-Allow-Headers", "X-MYRESPONSEHEADER");
		response.getWriter().print(jobj);
		response.flushBuffer();
		/**** This is the original code that let browser saves the file.
		response.reset();		
		response.setCharacterEncoding("utf-8");
		response.setContentLength(stream.size());
		response.setStatus(HttpStatus.OK.value());
		response.setHeader("Content-disposition", "attachment; filename=\""
				+ filename + "." + mime.name().toLowerCase() + "\"");

		IOUtils.write(stream.toByteArray(), response.getOutputStream());
		response.flushBuffer();
		****/
	}

	@RequestMapping(value = "/demo", method = RequestMethod.GET)
	public String demo() {
		return "demo";
	}

	/* catch all GET requests and redirect those */
	@RequestMapping(method = RequestMethod.GET)
	public String getAll() {
		return "redirect:demo";
	}

	@ExceptionHandler(IOException.class)
	public ModelAndView handleIOException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView.addObject("message", ex.getMessage());
		return modelAndView;
	}

	@ExceptionHandler(TimeoutException.class)
	public ModelAndView handleTimeoutException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView
				.addObject(
						"message",
						"Timeout converting SVG, is your file this big, or maybe you have a syntax error in the javascript callback?");
		return modelAndView;
	}

	@ExceptionHandler(PoolException.class)
	public ModelAndView handleServerPoolException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView
				.addObject(
						"message",
						"Sorry, the server is handling too many requests at the moment. Please try again.");
		return modelAndView;
	}

	@ExceptionHandler(SVGConverterException.class)
	public ModelAndView handleSVGRasterizeException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView
				.addObject(
						"message",
						"Something went wrong while converting.");
		return modelAndView;
	}

	@ExceptionHandler(InterruptedException.class)
	public ModelAndView handleInterruptedException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView
				.addObject(
						"message",
						"It took too long time to process the options, no SVG is created. Make sure your javascript is correct");
		return modelAndView;
	}

	@ExceptionHandler(ServletException.class)
	public ModelAndView handleServletException(Exception ex) {
		ModelAndView modelAndView = new ModelAndView();
		modelAndView.setViewName("error");
		modelAndView.addObject("message", ex.getMessage());
		return modelAndView;
	}


	/* TEST */
	@RequestMapping(value = "/test/{fileName}", method = RequestMethod.GET)
	public ResponseEntity<byte[]> staticImagesDownload(
	                 @PathVariable("fileName") String fileName) throws IOException {

	    String imageLoc = servletContext.getRealPath("WEB-INF/benchmark");
	    FileInputStream fis = new FileInputStream(imageLoc + "/" + fileName + ".png");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        try {
            for (int readNum; (readNum = fis.read(buf)) != -1;) {
                bos.write(buf, 0, readNum);
            }
        } catch (IOException ex) {
            // nothing here
        } finally {
        	fis.close();
        }

	    HttpHeaders responseHeaders = httpHeaderAttachment("TEST-" + fileName,  MimeType.PNG,
				bos.size());
		return new ResponseEntity<byte[]>(bos.toByteArray(),
				responseHeaders, HttpStatus.OK);
	}


	/* end TEST */


	/*
	 * Util methods
	 */

	public static HttpHeaders httpHeaderAttachment(final String filename,
			final MimeType mime, final int fileSize) {
		HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("charset", "utf-8");
		responseHeaders
				.setContentType(MediaType.parseMediaType(mime.getType()));
		responseHeaders.setContentLength(fileSize);
		responseHeaders.set("Content-disposition", "attachment; filename=\""
				+ filename + "." + mime.name().toLowerCase() + "\"");
		return responseHeaders;
	}

	private String getFilename(String name) {
		name = sanitize(name);
		return (name != null) ? name : "chart";
	}

	private static MimeType getMime(String mime) {
		MimeType type = MimeType.get(mime);
		if (type != null) {
			return type;
		}
		return MimeType.PNG;
	}

	private static String sanitize(String parameter) {
		if (parameter != null && !parameter.trim().isEmpty() && !(parameter.compareToIgnoreCase("undefined") == 0)) {
			return parameter.trim();
		}
		return null;
	}

	private static Float widthToFloat(String width) {
		width = sanitize(width);
		if (width != null) {
			Float parsedWidth = Float.valueOf(width);
			if (parsedWidth.compareTo(MAX_WIDTH) > 0) {
				return MAX_WIDTH;
			}
			if (parsedWidth.compareTo(0.0F) > 0) {
				return parsedWidth;
			}
		}
		return null;
	}

	private static Float scaleToFloat(String scale) {
		scale = sanitize(scale);
		if (scale != null) {
			Float parsedScale = Float.valueOf(scale);
			if (parsedScale.compareTo(MAX_SCALE) > 0) {
				return MAX_SCALE;
			} else if (parsedScale.compareTo(0.0F) > 0) {
				return parsedScale;
			}
		}
		return null;
	}
}