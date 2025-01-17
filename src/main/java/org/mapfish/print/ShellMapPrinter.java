/*
 * Copyright (C) 2013  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.json.JSONException;
import org.json.JSONWriter;
import org.mapfish.print.utils.PJsonObject;
import org.pvalsecc.misc.FileUtilities;
import org.pvalsecc.opts.GetOptions;
import org.pvalsecc.opts.InvalidOption;
import org.pvalsecc.opts.Option;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.itextpdf.text.DocumentException;

/**
 * A shell version of the MapPrinter. Can be used for testing or for calling
 * from other languages than Java.
 */
public class ShellMapPrinter {
    public static final Logger LOGGER = LogManager.getLogger(ShellMapPrinter.class);

    public static final String DEFAULT_SPRING_CONTEXT = "mapfish-spring-application-context.xml";

    @Option(desc = "Filename for the configuration (templates&CO)", mandatory = true)
    private String config = null;

    @Option(desc = "The location of the description of what has to be printed. By default, STDIN")
    private String spec = null;

    @Option(desc = "Used only if log4jConfig is not specified. 3 if you want everything, 2 if you want the debug information (stacktraces are shown), 1 for infos and 0 for only warnings and errors")
    private int verbose = 1;

    @Option(desc = "The destination file. By default, STDOUT")
    private String output = null;

    @Option(desc = "Get the config for the client form. Doesn't generate a PDF")
    private boolean clientConfig = false;

    @Option(desc = "Referer address to use when doing queries")
    private String referer = null;

    @Option(desc = "Cookie to use when doing queries")
    private String cookie = null;

    @Option(desc = "Property file for the log4j configuration")
    private String log4jConfig = null;

    @Option(desc = "Spring configuration file to use in addition to the default.  This allows overriding certain values if desired")
    private String springConfig = null;

    private AbstractXmlApplicationContext context;

    public ShellMapPrinter(String[] args) throws IOException {
        try {
            GetOptions.parse(args, this);
        } catch (InvalidOption invalidOption) {
            help(invalidOption.getMessage());
        }
        configureLogs();
        this.context = new ClassPathXmlApplicationContext(DEFAULT_SPRING_CONTEXT);

        if(springConfig != null) {
            this.context = new ClassPathXmlApplicationContext(new String[]{DEFAULT_SPRING_CONTEXT, springConfig});
        }
    }

    private void help(String message) {
        System.err.println(message);
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  " + getClass().getName() + " " + GetOptions.getShortList(this));
        System.err.println("Params:");
        try {
            System.err.println(GetOptions.getLongList(this));
        } catch (IllegalAccessException e) {
            e.printStackTrace(System.err);
        }
        System.exit(-1);
    }

    public void run() throws IOException, JSONException, DocumentException, InterruptedException {
        MapPrinter printer = context.getBean(MapPrinter.class);
        printer.setYamlConfigFile(new File(config));
        OutputStream outFile = null;
        try {
            if (clientConfig) {
                outFile = getOutputStream("");
                final OutputStreamWriter writer = new OutputStreamWriter(outFile, Charset.forName("UTF-8"));
                JSONWriter json = new JSONWriter(writer);
                json.object();
                {
                    printer.printClientConfig(json);
                }
                json.endObject();

                writer.close();

            } else {
                final InputStream inFile = getInputStream();
                final PJsonObject jsonSpec = MapPrinter.parseSpec(FileUtilities.readWholeTextStream(inFile, "UTF-8"));
                outFile = getOutputStream(printer.getOutputFormat(jsonSpec).getFileSuffix());
                Map<String, String> headers = new HashMap<String, String>();
                if (referer != null) {
                    headers.put("Referer", referer);
                }
                if (cookie != null) {
                    headers.put("Cookie", cookie);
                }
                printer.print(jsonSpec, outFile, headers);
            }
        } finally {
            if(outFile != null) outFile.close();
            if(context != null) context.destroy();
        }
    }



    private void configureLogs() throws IOException {
        URI uri=null;
        if (log4jConfig != null) {
            uri=new File(log4jConfig).toURI();
        } else {
            final ClassLoader classLoader = ShellMapPrinter.class.getClassLoader();
            URL log4jProp;
            switch (verbose) {
                case 0:
                    log4jProp = classLoader.getResource("shell-quiet-log4j.properties");
                    break;
                case 1:
                    log4jProp = classLoader.getResource("shell-info-log4j.properties");
                    break;
                case 2:
                    log4jProp = classLoader.getResource("shell-default-log4j.properties");
                    break;
                case 3:
                    log4jProp = classLoader.getResource("shell-verbose-log4j.properties");
                    break;
                default:
                    log4jProp = classLoader.getResource("shell-default-log4j.properties");
                    break;
            }

            try {
                uri=log4jProp.toURI();
            } catch (URISyntaxException e) {
                throw new IOException("Cannot load log4j2 properties. Error is: ",e);
            }
        }
        LoggerContext context = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);

        context.setConfigLocation(uri);
    }

    @SuppressWarnings("resource")
    private OutputStream getOutputStream(String suffix) throws FileNotFoundException {
        final OutputStream outFile;
        if (output != null) {
            if(!output.endsWith("."+suffix)) {
                output = output + "."+suffix;
            }
            outFile = new FileOutputStream(output);
        } else {
            //noinspection UseOfSystemOutOrSystemErr
            outFile = System.out;
        }
        return outFile;
    }

    @SuppressWarnings("resource")
    private InputStream getInputStream() throws FileNotFoundException {
        final InputStream file;
        if (spec != null) {
            file = new FileInputStream(spec);
        } else {
            file = System.in;
        }
        return file;
    }

    public static void main(String[] args) throws IOException, JSONException, DocumentException, InterruptedException {
        ShellMapPrinter app = new ShellMapPrinter(args);
        try {
            app.run();
        } catch (PrintException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("Cannot generate PDF", e);
            } else {
                LOGGER.error(e.toString());
            }
            System.exit(-2);
        }
    }
}
