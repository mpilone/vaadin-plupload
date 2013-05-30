package org.mpilone.vaadin;

import static java.lang.String.format;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.server.*;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Upload.Receiver;

/**
 * A {@link RequestHandler} implementation which will return the Flash and
 * Silverlight component as well as handle the incoming data upload from the
 * Plupload component.
 * 
 * @author mpilone
 */
class PluploadRequestHandler implements RequestHandler {

  /**
   * A progress listener that will be notified of percentage uploaded as
   * incoming data is read off the stream. A listener is only useful when the
   * runtime will post the entire file in a single request without generating
   * progress events on the client side. For example, the html4 runtime will
   * behave this way. If a listener is used with chunked uploading, the progress
   * of each chunk will be reported individually which is probably not useful to
   * consumers of the data.
   * 
   * @author mpilone
   */
  public static interface ProgressListener {
    /**
     * Called when the percentage uploaded changes.
     * 
     * @param percent
     *          the percent of the file (or chunk) uploaded
     */
    public void percentChanged(int percent);
  }

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * The root URL for all requests that should be handled by this handler.
   */
  private String url;

  /**
   * The upload receiver which will create output streams as files are uploaded.
   */
  private Upload.Receiver receiver;

  /**
   * The output stream currently being written.
   */
  private OutputStream receiverOutstream;

  /**
   * The optional progress listener that will be notified of data upload
   * progress.
   */
  private ProgressListener progressListener;

  /**
   * The date format to use when generating date strings for HTTP headers.
   */
  private final static SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat(
      "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

  static {
    HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  /**
   * Constructs the handler which will respond to requests to the given URL.
   * 
   * @param url
   */
  public PluploadRequestHandler(String url) {
    this.url = url;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.vaadin.server.RequestHandler#handleRequest(com.vaadin.server.
   * VaadinSession, com.vaadin.server.VaadinRequest,
   * com.vaadin.server.VaadinResponse)
   */
  @Override
  public boolean handleRequest(VaadinSession session, VaadinRequest request,
      VaadinResponse response) throws IOException {

    HttpServletRequest servletRequest = ((VaadinServletRequest) request)
        .getHttpServletRequest();

    String requestPath = servletRequest.getContextPath()
        + servletRequest.getServletPath() + servletRequest.getPathInfo();
    log.info("Got request for " + requestPath);

    if (requestPath.equals(url + "/upload")) {
      handleUploadRequest(session, request, response);
      return true;
    }
    else if (requestPath.equals(url + "/flash")) {
      handleFlashRequest(session, request, response);
      return true;
    }
    else if (requestPath.equals(url + "/silverlight")) {
      handleSilverlightRequest(session, request, response);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Handles the request for the Silverlight upload component and writes the
   * component binary to the response.
   * 
   * @param session
   *          the HTTP session
   * @param request
   *          the HTTP request
   * @param response
   *          the HTTP response
   * @throws IOException
   */
  private void handleSilverlightRequest(VaadinSession session,
      VaadinRequest request, VaadinResponse response) throws IOException {
    log.debug("Returning silverlight upload component.");

    InputStream instream = getClass().getResourceAsStream(
        "plupload/plupload.silverlight.swf");
    byte[] data = readAll(instream);
    instream.close();

    OutputStream outstream = response.getOutputStream();
    response.setContentType("application/x-silverlight-app");
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader("Content-Length", String.valueOf(data.length));
    outstream.write(data);
    outstream.close();

    log.debug("Wrote silverlight upload component: " + data.length);
  }

  /**
   * Handles the request for the Flash upload component and writes the component
   * binary to the response.
   * 
   * @param session
   *          the HTTP session
   * @param request
   *          the HTTP request
   * @param response
   *          the HTTP response
   * @throws IOException
   */
  private void handleFlashRequest(VaadinSession session, VaadinRequest request,
      VaadinResponse response) throws IOException {
    log.debug("Returning flash upload component.");

    InputStream instream = getClass().getResourceAsStream(
        "plupload/plupload.flash.swf");
    byte[] data = readAll(instream);
    instream.close();

    OutputStream outstream = response.getOutputStream();
    response.setContentType("application/x-shockwave-flash");
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader("Content-Length", String.valueOf(data.length));
    outstream.write(data);
    outstream.close();

    log.debug("Wrote flash upload component: " + data.length);
  }

  /**
   * Handles the data upload request. The data will be read from the request and
   * an OK response written. All data read will be immediately written to the
   * output stream returned by the .
   * 
   * @param session
   *          the HTTP session
   * @param request
   *          the HTTP request
   * @param response
   *          the HTTP response
   * @throws IOException
   */
  private void handleUploadRequest(VaadinSession session,
      VaadinRequest request, VaadinResponse response) throws IOException {

    // Default to OK.
    response.setStatus(HttpServletResponse.SC_OK);

    // Headers for no caching
    // HTTP headers for no cache etc
    response.setHeader("Expires", "Mon, 26 Jul 1997 05:00:00 GMT");
    response.setHeader("Last-Modified", HTTP_DATE_FORMAT.format(new Date()));
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
    response.setHeader("Cache-Control", "post-check=0, pre-check=0");
    response.setHeader("Pragma", "no-cache");

    // Check that we have a file upload request
    boolean isMultipart = ServletFileUpload
        .isMultipartContent((VaadinServletRequest) request);

    if (isMultipart) {
      handleMultipartUploadRequest(request, response);
    }
    else {
      log.info("Not a multipart request! Not sure what to do with it yet.");
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    // Write some content back. It doesn't matter what, but some runtimes (like
    // HTML4) don't like a content-length of 0 in the response.
    response.setHeader("Content-Length", "4");
    response.getOutputStream().write("DONE".getBytes());
  }

  private void handleMultipartUploadRequest(VaadinRequest request,
      VaadinResponse response) {
    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload();

    try {
      // Default to a single chunk in the event that chunking isn't enabled.
      int chunk = 0;
      // int chunks = 0;
      String fileName = null;

      String headerValue = request.getHeader("Content-Length");
      int contentLength = headerValue == null ? -1 : Integer
          .valueOf(headerValue);

      // Parse the request
      FileItemIterator iter = upload
          .getItemIterator((VaadinServletRequest) request);

      while (iter.hasNext()) {
        FileItemStream item = iter.next();
        String name = item.getFieldName();
        InputStream instream = item.openStream();
        String contentType = item.getContentType();

        if (item.isFormField()) {
          if (name.equals("chunk")) {
            chunk = Integer.valueOf(Streams.asString(instream));
          }
          if (name.equals("name")) {
            fileName = Streams.asString(instream);
          }
          // if (name.equals("chunks")) {
          // chunks = Integer.valueOf(Streams.asString(stream));
          // }
        }
        else {
          log.debug("File field " + name + " with file name " + item.getName()
              + " detected.");

          OutputStream outstream = getOutputStream(fileName, contentType, chunk);
          copyStream(instream, outstream, contentLength, progressListener);
        }
      }

    }
    catch (Exception ex) {
      log.error("File upload failed.", ex);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Copies the input stream to the output stream and updates the progress
   * listener if possible.
   * 
   * @param instream
   *          the input stream to read
   * @param outstream
   *          the output stream to write
   * @param contentLength
   *          the total content length (may be more than the raw data on the
   *          input stream)
   * @param listener
   *          the listener to notify when the percent read changes
   * @throws IOException
   */
  private void copyStream(InputStream instream, OutputStream outstream,
      int contentLength, ProgressListener listener) throws IOException {

    byte[] buf = new byte[2048];
    int read = 0;
    int totalRead = 0;
    int percent = 0;

    // log.debug(format(
    // "Copying upload stream with content length [%d] and notifying listener [%s].",
    // contentLength, listener));

    while ((read = instream.read(buf)) != -1) {
      outstream.write(buf, 0, read);
      totalRead += read;

      // Update the progress information if needed.
      if (contentLength > 0 && listener != null) {
        int newPercent = (int) ((totalRead / (float) contentLength) * 100);

        // If the percent complete changed, execute the callback with the
        // change.
        if (newPercent != percent) {
          percent = newPercent;
          listener.percentChanged(percent);
        }
      }
    }

    outstream.flush();
  }

  /**
   * Returns the output stream for the given file. Normally this method
   * delegates to the receiver set with {@link #setReceiver(Receiver)} but if no
   * receiver has been set, an output stream that writes to /dev/null will be
   * returned. This method can safely be called multiple times for different
   * chunks but only the first chunk will create a new output stream and all
   * other chunks will reuse the existing stream.
   * 
   * @param fileName
   *          the name of the file being uploaded (if known)
   * @param contentType
   *          the content type of the file being uploaded (if known)
   * @param chunk
   *          the chunk number (0 based)
   * @return the output stream to which to write the incoming data
   */
  private OutputStream getOutputStream(String fileName, String contentType,
      int chunk) {

    log.debug(format("Getting output stream for chunk %d in file %s.", chunk,
        fileName));

    // If this is the first chunk and we have a receiver, create a new output
    // stream.
    if (chunk == 0 && receiver != null) {
      receiverOutstream = receiver.receiveUpload(fileName, contentType);
    }

    // If we don't have a receiver, write to/dev/null.
    if (receiverOutstream == null) {
      log.warn(format("Incoming file data but no receiver has "
          + "been set for chunk [%d] in file [%s]. Incoming data "
          + "will be ignored.", chunk, fileName));

      receiverOutstream = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          // send to /dev/null
        }
      };
    }

    return receiverOutstream;
  }

  /**
   * Reads all of the data available from the given input stream and returns it
   * as a block of bytes.
   * 
   * @param instream
   *          the input stream from which to read
   * @return the data read or an empty array if no data was read
   * @throws IOException
   */
  private byte[] readAll(InputStream instream) throws IOException {
    ByteArrayOutputStream outstream = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int read;
    while ((read = instream.read(buf)) != -1) {
      outstream.write(buf, 0, read);
    }
    outstream.close();
    return outstream.toByteArray();
  }

  /**
   * The receiver to use to create output streams for incoming data.
   * 
   * @param receiver
   *          the receiver to use to create output streams
   */
  public void setReceiver(Receiver receiver) {
    this.receiver = receiver;
  }

  /**
   * Sets the listener to be notified when the percent uploaded changes during a
   * data request.
   * 
   * @param progressListener
   *          the progress listener
   */
  public void setProgressListener(ProgressListener progressListener) {
    this.progressListener = progressListener;
  }
}
