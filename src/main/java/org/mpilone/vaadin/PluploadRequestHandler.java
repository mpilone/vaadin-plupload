package org.mpilone.vaadin;

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
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.server.*;
import com.vaadin.server.communication.*;

/**
 * A {@link RequestHandler} implementation which will return the Flash and
 * Silverlight component as well as handle the incoming data upload from the
 * Plupload component.
 *
 * @author mpilone
 */
class PluploadRequestHandler implements RequestHandler {

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
  private final String url;

  /**
   * The date format to use when generating date strings for HTTP headers.
   */
  private final static SimpleDateFormat HTTP_DATE_FORMAT = new SimpleDateFormat(
      "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

  static {
    HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final StreamVariable streamVariable;

  private static final int MAX_UPLOAD_BUFFER_SIZE = 4096;

  /**
   * Constructs the handler which will respond to requests to the given URL.
   *
   * @param url the base URL to watch for incoming requests
   * @param streamVariable the stream variable to write to as data arrives
   */
  public PluploadRequestHandler(String url, StreamVariable streamVariable) {
    this.url = url;
    this.streamVariable = streamVariable;
  }

  @Override
  public boolean handleRequest(VaadinSession session, VaadinRequest request,
      VaadinResponse response) throws IOException {

    HttpServletRequest servletRequest = ((VaadinServletRequest) request)
        .getHttpServletRequest();

    String requestPath = servletRequest.getContextPath()
        + servletRequest.getServletPath() + servletRequest.getPathInfo();
    log.info("Handler {} got request for {}", System.identityHashCode(this),
        requestPath);

    if (requestPath.equals(url + "/upload")) {
      handleUploadRequest(session, request, response);
      return true;
    }
// No longer used. ClassResources are used instead.
//    else if (requestPath.equals(url + "/flash")) {
//      handleFlashRequest(session, request, response);
//      return true;
//    }
//    else if (requestPath.equals(url + "/silverlight")) {
//      handleSilverlightRequest(session, request, response);
//      return true;
//    }
    else {
      return false;
    }
  }

//  /**
//   * Handles the request for the Silverlight upload component and writes the
//   * component binary to the response.
//   *
//   * @param session the HTTP session
//   * @param request the HTTP request
//   * @param response the HTTP response
//   *
//   * @throws IOException
//   */
//  private void handleSilverlightRequest(VaadinSession session,
//      VaadinRequest request, VaadinResponse response) throws IOException {
//    log.debug("Returning silverlight upload component.");
//
//    byte[] data;
//    try (InputStream instream =
//        getClass().getResourceAsStream("plupload/Moxie.xap")) {
//      data = readAll(instream);
//    }
//
//    try (OutputStream outstream = response.getOutputStream()) {
//      response.setContentType("application/x-silverlight-app");
//      response.setStatus(HttpServletResponse.SC_OK);
//      response.setHeader("Content-Length", String.valueOf(data.length));
//      outstream.write(data);
//    }
//
//    log.debug("Wrote silverlight upload component: " + data.length);
//  }
//
//  /**
//   * Handles the request for the Flash upload component and writes the component
//   * binary to the response.
//   *
//   * @param session the HTTP session
//   * @param request the HTTP request
//   * @param response the HTTP response
//   *
//   * @throws IOException
//   */
//  private void handleFlashRequest(VaadinSession session, VaadinRequest request,
//      VaadinResponse response) throws IOException {
//    log.debug("Returning flash upload component.");
//
//    byte[] data;
//    try (InputStream instream =
//        getClass().getResourceAsStream("plupload/Moxie.swf")) {
//      data = readAll(instream);
//    }
//    try (OutputStream outstream = response.getOutputStream()) {
//      response.setContentType("application/x-shockwave-flash");
//      response.setStatus(HttpServletResponse.SC_OK);
//      response.setHeader("Content-Length", String.valueOf(data.length));
//      outstream.write(data);
//    }
//
//    log.debug("Wrote flash upload component: " + data.length);
//  }

  /**
   * Handles the data upload request. The data will be read from the request and
   * an OK response written. All data read will be immediately written to the
   * output stream returned by the .
   *
   * @param session the HTTP session
   * @param request the HTTP request
   * @param response the HTTP response
   *
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
      handleMultipartUploadRequest(session, request, response);
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

  private void handleMultipartUploadRequest(VaadinSession session,
      VaadinRequest request, VaadinResponse response) {
    // Create a new file upload handler
    ServletFileUpload upload = new ServletFileUpload();

    try {
      // Default to a single chunk in the event that chunking isn't enabled.
      int chunk = 0;
      int chunks = 1;
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

        if (item.isFormField()) {
          switch (name) {
            case "chunk":
              chunk = Integer.valueOf(Streams.asString(instream));
              break;
            case "chunks":
              chunks = Integer.valueOf(Streams.asString(instream));
              break;
            case "name":
              fileName = Streams.asString(instream);
              if (fileName != null) {
                fileName = FilenameUtils.getName(fileName);
              }
              break;
            default:
              // ignore
              break;
          }
        }
        else {
          log.debug("File field {} with file name {} detected.", name, item.
              getName());

          streamToReceiver(session, instream, streamVariable, fileName,
              item.getContentType(), contentLength, chunk, chunks);
        }
      }
    }
    catch (Exception ex) {
      log.error("File upload failed.", ex);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * @param in
   * @param streamVariable
   * @param filename
   * @param type
   * @param contentLength
   *
   * @return true if the streamvariable has informed that the terminal can
   * forget this variable
   * @throws UploadException
   */
  protected final boolean streamToReceiver(VaadinSession session,
      final InputStream in, StreamVariable streamVariable,
      String filename, String type, int contentLength, int chunk, int chunks)
      throws UploadException {

    if (streamVariable == null) {
      throw new IllegalStateException("StreamVariable for the post not found");
    }

    OutputStream out = null;
    int totalBytes = 0;
    StreamingStartEventImpl startedEvent = new StreamingStartEventImpl(
        filename, type, contentLength);
    try {
      boolean listenProgress;
      session.lock();
      try {
        streamVariable.streamingStarted(startedEvent);
        out = streamVariable.getOutputStream();
        listenProgress = streamVariable.listenProgress();
      }
      finally {
        session.unlock();
      }

      // Gets the output target stream
      if (out == null) {
        throw new NoOutputStreamException();
      }

      if (in == null) {
        // No file, for instance non-existent filename in html upload
        throw new NoInputStreamException();
      }

      log.info("Handler {} streaming content to stream variable {}",
          System.identityHashCode(this),
          System.identityHashCode(streamVariable));

      final byte buffer[] = new byte[MAX_UPLOAD_BUFFER_SIZE];
      int bytesReadToBuffer;

      while ((bytesReadToBuffer = in.read(buffer)) > 0) {
        out.write(buffer, 0, bytesReadToBuffer);
        totalBytes += bytesReadToBuffer;

        if (listenProgress) {
          // Update progress if listener set and contentLength received
          session.lock();
          try {
            StreamingProgressEventImpl progressEvent =
                new StreamingProgressEventImpl(filename, type, contentLength,
                    totalBytes);
            streamVariable.onProgress(progressEvent);
          }
          finally {
            session.unlock();
          }
        }
        if (streamVariable.isInterrupted()) {
          throw new FileUploadHandler.UploadInterruptedException();
        }
      }

      // upload successful
      out.flush();
      StreamVariable.StreamingEndEvent event = new StreamingEndEventImpl(
          filename, type, totalBytes);
        session.lock();
        try {
          streamVariable.streamingFinished(event);
        }
        finally {
          session.unlock();
        }
    }
    catch (FileUploadHandler.UploadInterruptedException e) {
      // Download interrupted by application code
//      tryToCloseStream(out);
      StreamVariable.StreamingErrorEvent event = new StreamingErrorEventImpl(
          filename, type, contentLength, totalBytes, e);

      session.lock();
      try {
        streamVariable.streamingFailed(event);
      }
      finally {
        session.unlock();
      }
      // Note, we are not throwing interrupted exception forward as it is
      // not a terminal level error like all other exception.
    }
    catch (final Exception e) {
//      tryToCloseStream(out);
      session.lock();
      try {
        StreamVariable.StreamingErrorEvent event = new StreamingErrorEventImpl(
            filename, type, contentLength, totalBytes, e);
        streamVariable.streamingFailed(event);
        // throw exception for terminal to be handled (to be passed to
        // terminalErrorHandler)
        throw new UploadException(e);
      }
      finally {
        session.unlock();
      }
    }
    return startedEvent.isDisposed();
  }

  private static void tryToCloseStream(OutputStream out) {
    try {
      // try to close output stream (e.g. file handle)
      if (out != null) {
        out.close();
      }
    }
    catch (IOException e1) {
      // NOP
    }
  }

//  /**
//   * Copies the input stream to the output stream and updates the progress
//   * listener if possible.
//   *
//   * @param instream
//   *          the input stream to read
//   * @param outstream
//   *          the output stream to write
//   * @param contentLength
//   *          the total content length (may be more than the raw data on the
//   *          input stream)
//   * @throws IOException
//   */
//  private void copyStream(InputStream instream, OutputStream outstream,
//      int contentLength) throws IOException {
//
//    byte[] buf = new byte[2048];
//    int read;
//    int totalRead = 0;
//    int percent = 0;
//
//    // log.debug(format(
//    // "Copying upload stream with content length [%d] and notifying listener [%s].",
//    // contentLength, listener));
//
//    while ((read = instream.read(buf)) != -1) {
//      outstream.write(buf, 0, read);
//      totalRead += read;
//
//      // Update the progress information if needed.
//      if (contentLength > 0 && listener != null) {
//        int newPercent = (int) ((totalRead / (float) contentLength) * 100);
//
//        // If the percent complete changed, execute the callback with the
//        // change.
//        if (newPercent != percent) {
//          percent = newPercent;
//          listener.percentChanged(percent);
//        }
//      }
//    }
//
//    outstream.flush();
//  }
//  /**
//   * Returns the output stream for the given file. Normally this method
//   * delegates to the receiver set with {@link #setReceiver(Receiver)} but if no
//   * receiver has been set, an output stream that writes to /dev/null will be
//   * returned. This method can safely be called multiple times for different
//   * chunks but only the first chunk will create a new output stream and all
//   * other chunks will reuse the existing stream.
//   *
//   * @param fileName
//   *          the name of the file being uploaded (if known)
//   * @param contentType
//   *          the content type of the file being uploaded (if known)
//   * @param chunk
//   *          the chunk number (0 based)
//   * @return the output stream to which to write the incoming data
//   */
//  private OutputStream getOutputStream(String fileName, String contentType,
//      int chunk) {
//
//    log.debug(format("Getting output stream for chunk %d in file %s.", chunk,
//        fileName));
//
//    // If this is the first chunk and we have a receiver, create a new output
//    // stream.
//    if (chunk == 0 && streamVariable != null) {
//
//      receiverOutstream = streamVariable.getOutputStream();
//    }
//
//    // If we don't have a receiver, write to/dev/null.
//    if (receiverOutstream == null) {
//      log.warn(format("Incoming file data but no receiver has "
//          + "been set for chunk [%d] in file [%s]. Incoming data "
//          + "will be ignored.", chunk, fileName));
//
//      receiverOutstream = new OutputStream() {
//        @Override
//        public void write(int b) throws IOException {
//          // send to /dev/null
//        }
//      };
//    }
//
//    return receiverOutstream;
//  }
  /**
   * Reads all of the data available from the given input stream and returns it
   * as a block of bytes.
   *
   * @param instream the input stream from which to read
   *
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
//
//  /**
//   * The receiver to use to create output streams for incoming data.
//   *
//   * @param receiver
//   *          the receiver to use to create output streams
//   */
//  public void setReceiver(Receiver receiver) {
//    this.receiver = receiver;
//  }
//
//  /**
//   * Sets the listener to be notified when the percent uploaded changes during a
//   * data request.
//   *
//   * @param progressListener
//   *          the progress listener
//   */
//  public void setProgressListener(ProgressListener progressListener) {
//    this.progressListener = progressListener;
//  }

  public static class StreamEventImpl implements StreamVariable.StreamingEvent {

    private final String fileName;
    private final String mimeType;
    private final long contentLength;
    private final long bytesReceived;

    public StreamEventImpl(String fileName, String mimeType, long contentLength,
        long bytesReceived) {
      this.fileName = fileName;
      this.mimeType = mimeType;
      this.contentLength = contentLength;
      this.bytesReceived = bytesReceived;
    }

    @Override
    public String getFileName() {
      return this.fileName;
    }

    @Override
    public String getMimeType() {
      return this.mimeType;
    }

    @Override
    public long getContentLength() {
      return this.contentLength;
    }

    @Override
    public long getBytesReceived() {
      return this.bytesReceived;
    }

  }

  public static class StreamingStartEventImpl extends StreamEventImpl implements
      StreamVariable.StreamingStartEvent {
    private boolean disposed;

    private StreamingStartEventImpl(String filename, String type,
        int contentLength) {
      super(filename, type, contentLength, 0);
    }

    @Override
    public void disposeStreamVariable() {
      this.disposed = true;
    }

    private boolean isDisposed() {
      return this.disposed;
    }

  }

  public static class StreamingEndEventImpl extends StreamEventImpl implements
      StreamVariable.StreamingEndEvent {

    private StreamingEndEventImpl(String filename, String type, int totalBytes) {
      super(filename, type, totalBytes, totalBytes);
    }

  }

  public static class StreamingProgressEventImpl extends StreamEventImpl
      implements StreamVariable.StreamingProgressEvent {

    private StreamingProgressEventImpl(String filename, String type,
        int contentLength, int totalBytes) {
      super(filename, type, contentLength, totalBytes);
    }

  }

  public static class StreamingErrorEventImpl extends StreamEventImpl
      implements StreamVariable.StreamingErrorEvent {
    private Exception exception;

    private StreamingErrorEventImpl(String filename, String type,
        int contentLength, int totalBytes, Exception e) {
      super(filename, type, contentLength, totalBytes);
      this.exception = e;
    }

    @Override
    public Exception getException() {
      return this.exception;
    }

  }
}
