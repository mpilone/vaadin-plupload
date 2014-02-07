
package org.mpilone.vaadin;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.mpilone.vaadin.shared.PluploadError;
import org.mpilone.vaadin.shared.PluploadServerRpc;
import org.mpilone.vaadin.shared.PluploadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.annotations.JavaScript;
import com.vaadin.server.*;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Upload;
import com.vaadin.util.FileTypeResolver;

/**
 * Wrapper for the Plupload HTML5/Flash/HTML4 upload component. You can find
 * more information at http://www.plupload.com/.
 *
 * TODO: this class needs to be documented and requires some more functionality
 * (such as more listeners and file removal support).
 *
 * @author mpilone
 */
@JavaScript({"plupload_connector.js", "plupload/plupload.full.min.js"})
public class Plupload extends AbstractJavaScriptComponent {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final static Method SUCCEEDED_METHOD;
  private final static Method STARTED_METHOD;
  private final static Method FINISHED_METHOD;
  private final static Method FAILED_METHOD;

  static {
    try {
      SUCCEEDED_METHOD = SucceededListener.class.getMethod("uploadSucceeded",
          SucceededEvent.class);
      FAILED_METHOD = FailedListener.class.getMethod("uploadFailed",
          FailedEvent.class);
      STARTED_METHOD = StartedListener.class.getMethod("uploadStarted",
          StartedEvent.class);
      FINISHED_METHOD = FinishedListener.class.getMethod("uploadFinished",
          FinishedEvent.class);
    }
    catch (NoSuchMethodException | SecurityException ex) {
      throw new RuntimeException("Unable to find listener event method.", ex);
    }
  }

  /**
   * The remote procedure call interface which allows calls from the client side
   * to the server. For the most part these methods map to the events generated
   * by the Plupload_orig JavaScript component.
   */
  private final PluploadServerRpc rpc = new PluploadServerRpc() {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    @Override
    public void onError(PluploadError error) {
      log.debug("onError: [" + error.getCode() + "] " + error.getMessage());

      fireUploadInterrupted(null, mimeType, contentLength, new RuntimeException(
          error.
          getMessage()));
      endUpload();
    }

    @Override
    public void onUploadFile(String filename, long contentLength) {
      log.info("Started upload of file {} with length {} to plupload {}",
          filename, contentLength, System.identityHashCode(this));

      Plupload.this.contentLength = contentLength;

      startUpload();
      fireStarted(filename, null);
    }

    @Override
    public void onFileUploaded(String filename, long contentLength) {
      log.info("Completed upload of file " + filename + " with length "
          + contentLength);

      fireUploadSuccess(filename, mimeType, contentLength);
      endUpload();
      markAsDirty();
    }

    @Override
    public void onInit(String runtime) {
      log.info("Uploader initialized with runtime {}", runtime);

      activeRuntime = runtime;
    }
  };

  /**
   * The request handler which can return the Flash and Silverlight component as
   * well as handle the incoming data upload from the Plupload_orig component.
   */
  //private PluploadRequestHandler requestHandler;

  private boolean interrupted;

  private StreamVariable streamVariable;

  private Upload.Receiver receiver;

  private long contentLength;

  private String mimeType;

  private long bytesRead;

  private String activeRuntime;

  private final List<Upload.ProgressListener> progressListeners =
      new ArrayList<>();

  private boolean uploading;

  /**
   * Constructs the upload component. A unique ID will be generated for the ID
   * as well as a request handler registered to handle incoming data. The
   * request handler will listen at
   * /[contextPath]/[servletPath]/plupload/[uniqueID]. The following defaults
   * will be used:
   * <ul>
   * <li>runtimes: html5,flash,silverlight,html4</li>
   * <li>chunkSize: null</li>
   * <li>maxFileSize: 10MB</li>
   * <li>multiSelection: false</li>
   * </ul>
   */
  public Plupload() {
    registerRpc(rpc);

    // Add the Silverlight mime-type if it isn't already in the resolver.
    if (FileTypeResolver.DEFAULT_MIME_TYPE.equals(FileTypeResolver.getMIMEType(
        "Moxie.xap"))) {
      FileTypeResolver.addExtension("xap", "application/x-silverlight-app");
    }

    setResource("flashSwfUrl", new ClassResource(getClass(),
        "plupload/Moxie.swf"));
    setResource("silverlightSwfUrl", new ClassResource(getClass(),
        "plupload/Moxie.xap"));

    setRuntimes("html5,flash,silverlight,html4");
    setChunkSize(null);
    setMaxFileSize(10 * 1024 * 1024L);
    getState().multiSelection = false;
  }

  @Override
  public void attach() {
    super.attach();

    String url = getSession().getCommunicationManager().
        getStreamVariableTargetUrl(this, "plupload", getStreamVariable());

    getState().url = url;
  }

  @Override
  public void detach() {
    log.debug("Cleaning up stream variable.");

    // Cleanup our stream variable.
    getUI().getConnectorTracker().cleanStreamVariable(getConnectorId(),
        "plupload");

    super.detach();
  }

  /**
   * Emits the upload success event.
   *
   * @param filename
   * @param mimeType
   * @param length
   *
   */
  protected void fireUploadSuccess(String filename, String mimeType,
      long length) {
    fireEvent(new SucceededEvent(this, filename, mimeType, length));
  }

  /**
   * Emits the progress event.
   *
   * @param totalBytes bytes received so far
   * @param contentLength actual size of the file being uploaded, if known
   *
   */
  protected void fireUpdateProgress(long totalBytes, long contentLength) {

    //log.info("Firing progress on plupload {}", System.identityHashCode(this));

    // This may be a progress event from a single chunk. We can ignore this
    // if we know we're going to have multiple chunks. If is a single chunk,
    // the content length may be greater than the overall content length
    // because of the extra fields sent in the multi-part request.
    if (this.contentLength == contentLength) {
      // this is implemented differently than other listeners to maintain
      // backwards compatibility
      if (progressListeners != null) {
        for (Upload.ProgressListener l : progressListeners) {
          l.updateProgress(totalBytes, this.contentLength);
        }
      }
    }
  }

  public StreamVariable getStreamVariable() {
    if (streamVariable == null) {
      streamVariable = new PluploadStreamVariable();
    }

    return streamVariable;
  }

  /**
   * Returns the runtime that was selected by the uploader after initialization
   * on the client. This information is useful for debugging but should have
   * little impact on functionality.
   *
   * @return the active runtime or null if one has not been selected
   */
  public String getActiveRuntime() {
    return activeRuntime;
  }

  /**
   * Adds the given listener for upload failed events.
   *
   * @param listener the listener to add
   */
  public void addFailedListener(FailedListener listener) {
    addListener(FailedEvent.class, listener, FAILED_METHOD);
  }

  /**
   * Adds the given listener for upload finished events.
   *
   * @param listener the listener to add
   */
  public void addFinishedListener(FinishedListener listener) {
    addListener(FinishedEvent.class, listener, FINISHED_METHOD);
  }

  /**
   * Adds the given listener for upload progress events.
   *
   * @param listener the listener to add
   */
  public void addProgressListener(Upload.ProgressListener listener) {
    progressListeners.add(listener);
  }

  /**
   * Adds the given listener for upload started events.
   *
   * @param listener the listener to add
   */
  public void addStartedListener(StartedListener listener) {
    addListener(StartedEvent.class, listener, STARTED_METHOD);
  }

  /**
   * Adds the given listener for upload succeeded events.
   *
   * @param listener the listener to add
   */
  public void addSucceededListener(SucceededListener listener) {
    addListener(SucceededEvent.class, listener, SUCCEEDED_METHOD);
  }

  /**
   * Removes the given listener for upload failed events.
   *
   * @param listener the listener to add
   */
  public void removeFailedListener(FailedListener listener) {
    removeListener(FailedEvent.class, listener, FAILED_METHOD);
  }

  /**
   * Removes the given listener for upload finished events.
   *
   * @param listener the listener to add
   */
  public void removeFinishedListener(FinishedListener listener) {
    removeListener(FinishedEvent.class, listener, FINISHED_METHOD);
  }

  /**
   * Removes the given listener for upload progress events.
   *
   * @param listener the listener to add
   */
  public void removeProgressListener(Upload.ProgressListener listener) {
    progressListeners.remove(listener);
  }

  /**
   * Removes the given listener for upload started events.
   *
   * @param listener the listener to add
   */
  public void removesStartedListener(StartedListener listener) {
    removeListener(StartedEvent.class, listener, STARTED_METHOD);
  }

  /**
   * Removes the given listener for upload succeeded events.
   *
   * @param listener the listener to add
   */
  public void removeSucceededListener(SucceededListener listener) {
    removeListener(SucceededEvent.class, listener, SUCCEEDED_METHOD);
  }

  /**
   * Emit upload received event.
   *
   * @param filename
   * @param mimeType
   */
  protected void fireStarted(String filename, String mimeType) {
    fireEvent(new StartedEvent(this, filename, mimeType,
        contentLength));
  }

  protected void fireNoInputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoInputStreamEvent(this, filename, mimeType,
        length));
  }

  protected void fireNoOutputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoOutputStreamEvent(this, filename, mimeType,
        length));
  }

  protected void fireUploadInterrupted(String filename, String mimeType,
      long length, Exception e) {
    fireEvent(new FailedEvent(this, filename, mimeType, length, e));
  }

  /**
   * Returns the caption displayed on the submit button or on the combination
   * browse and submit button when in immediate mode.
   *
   * @return the caption of the submit button
   */
  public String getButtonCaption() {
    return getState().buttonCaption;
  }

  /**
   * Sets the caption displayed on the submit button or on the combination
   * browse and submit button when in immediate mode. When not in immediate
   * mode, the text on the browse button cannot be set.
   *
   * @param caption the caption of the submit button
   */
  public void setButtonCaption(String caption) {
    getState().buttonCaption = caption;
  }

  public long getMaxRetries() {
    return getState().maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    getState().maxRetries = maxRetries;
  }

  public long getBytesRead() {
    return bytesRead;
  }

  public Upload.Receiver getReceiver() {
    return receiver;
  }

  /**
   * Sets the receiver that will be used to create output streams when a file
   * starts uploading. The file data will be written to the returned stream. If
   * not set, the uploaded data will be ignored. The receiver may be called
   * multiple times with different file names if there are multiple files in the
   * upload queue.
   *
   * @param receiver the receiver to use for creating file output streams
   */
  public void setReceiver(Upload.Receiver receiver) {
    this.receiver = receiver;
  }

  /**
   * Sets the size in bytes of each data chunk to be sent from the client to the
   * server. Not all runtimes support chunking. If set to null, chunking will be
   * disabled.
   *
   * @param size the size of each data chunk
   */
  public void setChunkSize(Long size) {
    getState().chunkSize = size;
  }

  /**
   * Sets the maximum size in bytes of files that may be selected and uploaded.
   *
   * @param size the maximum file size that may be uploaded
   */
  public void setMaxFileSize(long size) {
    getState().maxFileSize = size;
  }

  /**
   * Starts the upload of any files in the upload queue. Once started, the
   * uploads cannot be stopped until an error occurs or all the data is received
   * (this may change in the future).
   */
  public void submitUpload() {
    getState().submitUpload = true;
  }

  public long getUploadSize() {
    return contentLength;
  }

  /**
   * Interrupts the upload currently being received. The interruption will be
   * done by the receiving tread so this method will return immediately and the
   * actual interrupt will happen a bit later.
   */
  public void interruptUpload() {
    if (uploading) {
      interrupted = true;
    }
  }

  /**
   * Go into upload state. This is to prevent double uploading on same
   * component.
   *
   * Warning: this is an internal method used by the framework and should not be
   * used by user of the Upload component. Using it results in the Upload
   * component going in wrong state and not working. It is currently public
   * because it is used by another class.
   */
  public void startUpload() {
    if (uploading) {
      throw new IllegalStateException("uploading already started");
    }
    uploading = true;
    getState().submitUpload = false;
  }

  /**
   * Returns true if the component is enabled. This implementation always
   * returns true even if the component is set to disabled. This is required
   * because we want the ability to disable the browse/submit buttons while
   * still allowing an upload in progress to continue. The implemention relies
   * on RPC calls so the overall component must always be enabled or the upload
   * complete RPC call will be dropped.
   *
   * @return always true
   */
  @Override
  public boolean isConnectorEnabled() {
    return true;
  }

  /**
   * Go into state where new uploading can begin.
   *
   * Warning: this is an internal method used by the framework and should not be
   * used by user of the Upload component.
   */
  private void endUpload() {
    uploading = false;
    contentLength = -1;
    bytesRead = 0;
    interrupted = false;
    markAsDirty();
  }

  /**
   * Returns true if an upload is currently in progress.
   *
   * @return the upload in progress
   */
  public boolean isUploading() {
    return uploading;
  }

  /**
   * Sets the comma separated list of runtimes that the uploader will attempt to
   * use. It will try to initialize each runtime in order if one fails it will
   * move on to the next one.
   *
   * @param runtimes the comma separated list of runtimes
   */
  public void setRuntimes(String runtimes) {
    getState().runtimes = runtimes;
  }

  /**
   * Sets the comma separated list of runtimes that the uploader will attempt to
   * use.
   *
   * @return the comma separated list of runtimes
   */
  public String getRuntimes() {
    return getState().runtimes;
  }

  @Override
  protected PluploadState getState() {
    return (PluploadState) super.getState();
  }

  public static class FinishedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long length;

    public FinishedEvent(Component source, String filename, String mimeType,
        long length) {
      super(source);

      this.filename = filename;
      this.mimeType = mimeType;
      this.length = length;
    }

    public String getFilename() {
      return filename;
    }

    public String getMimeType() {
      return mimeType;
    }

    public long getLength() {
      return length;
    }

  }

  public static class NoInputStreamEvent extends FailedEvent {

    public NoInputStreamEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  public static class NoOutputStreamEvent extends FailedEvent {

    public NoOutputStreamEvent(Component source, String filename,
        String mimeType, long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  public interface FinishedListener {
    void uploadFinished(FinishedEvent evt);
  }

  public static class FailedEvent extends FinishedEvent {
    private final Exception reason;

    public FailedEvent(Component source, String filename, String mimeType,
        long length, Exception reason) {
      super(source, filename, mimeType, length);
      this.reason = reason;
    }

    public Exception getReason() {
      return reason;
    }

  }

  public interface FailedListener {
    void uploadFailed(FailedEvent evt);
  }

  public static class StartedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long contentLength;

    public StartedEvent(Component source, String filename, String mimeType,
        long contentLength) {
      super(source);
      this.filename = filename;
      this.mimeType = mimeType;
      this.contentLength = contentLength;
    }

    public String getFilename() {
      return filename;
    }

    public String getMimeType() {
      return mimeType;
    }

    public long getContentLength() {
      return contentLength;
    }


  }

  public interface StartedListener {
    void uploadStarted(StartedEvent evt);
  }

  public static class SucceededEvent extends FinishedEvent {

    public SucceededEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length);
    }

  }

  public interface SucceededListener {
    void uploadSucceeded(SucceededEvent evt);
  }

  private class PluploadStreamVariable implements
      com.vaadin.server.StreamVariable {

    private StreamVariable.StreamingStartEvent lastStartedEvent;
    private OutputStream outstream;

    @Override
    public boolean listenProgress() {
      return (progressListeners != null && !progressListeners
          .isEmpty());
    }

    @Override
    public void onProgress(StreamVariable.StreamingProgressEvent event) {
      fireUpdateProgress(event.getBytesReceived(),
          event.getContentLength());
    }

    @Override
    public boolean isInterrupted() {
      return interrupted;
    }

    @Override
    public OutputStream getOutputStream() {
      if (outstream == null) {
        outstream = receiver.receiveUpload(
            lastStartedEvent.getFileName(),
            lastStartedEvent.getMimeType());
      }

      // We don't want to permit closing of the output stream because
      // we may have a chunked upload that we need to write to the
      // same output stream.
      return new UncloseableOutputStream(outstream);
    }

    @Override
    public void streamingStarted(StreamVariable.StreamingStartEvent event) {
      lastStartedEvent = event;
    }

    @Override
    public void streamingFinished(StreamVariable.StreamingEndEvent event) {
          // Update the total bytes read. This is needed because this stream
      // may only be one of many chunks.
      bytesRead += event.getBytesReceived();
      fireUpdateProgress(bytesRead, contentLength);

      if (bytesRead == contentLength) {
        // This is the last chunk. Cleanup.

        outstream = null;
      }

      lastStartedEvent = null;
    }

    @Override
    public void streamingFailed(StreamVariable.StreamingErrorEvent event) {
      Exception exception = event.getException();
      if (exception instanceof NoInputStreamException) {
        fireNoInputStream(event.getFileName(),
            event.getMimeType(), 0);
      }
      else if (exception instanceof NoOutputStreamException) {
        fireNoOutputStream(event.getFileName(),
            event.getMimeType(), 0);
      }
      else {
        fireUploadInterrupted(event.getFileName(),
            event.getMimeType(), 0, exception);
      }
      tryClose(outstream);
      outstream = null;
      endUpload();
    }

    private void tryClose(Closeable closeable) {
      try {
        closeable.close();
      }
      catch (IOException ex) {
        // Ignore
      }
    }
  }

  private static class UncloseableOutputStream extends OutputStream {

    private OutputStream delegate;

    public UncloseableOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(byte[] b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void write(int b) throws IOException {
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      delegate.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
      delegate.flush();
    }
  }

}
