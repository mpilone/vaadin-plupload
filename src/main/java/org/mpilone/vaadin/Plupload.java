package org.mpilone.vaadin;

import static org.mpilone.vaadin.Streams.tryClose;
import static org.mpilone.vaadin.Streams.tryCommit;

import java.io.*;
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
import com.vaadin.server.communication.FileUploadHandler;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Upload;
import com.vaadin.util.FileTypeResolver;

/**
 * <p>
 * Wrapper for the Plupload HTML5/Flash/HTML4 upload component. You can find
 * more information at http://www.plupload.com/. This implementation attempts to
 * follow the {@link Upload} API as much as possible to be a drop-in
 * replacement.
 * </p>
 * <p>
 * The Plupload component announces the start of an upload via RPC which means
 * it is possible that the data could begin arriving at the Receiver before the
 * uploadStarted event is fired. Also, the filename passed to the Receiver
 * during output stream creation may be inaccurate as Plupload labels chunks
 * with a filename of "blob".
 * </p>
 * <p>
 * When using retries, the incoming data must be buffered in order to reset the
 * input stream in the event of a partial upload. Therefore it is recommend that
 * only small files be supported or chunking is used to limit the file size.
 * </p>
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

  private final PluploadServerRpc rpc = new PluploadServerRpcImpl();
  private StreamVariable streamVariable;
  private Upload.Receiver receiver;
  private Runtime runtime;
  private int maxRetryBufferSize = 0;
  private final List<Upload.ProgressListener> progressListeners =
      new ArrayList<>();
  private UploadSession uploadSession;

  /**
   * Constructs the upload component.
   *
   * @see #Plupload(java.lang.String, com.vaadin.ui.Upload.Receiver)
   */
  public Plupload() {
    this(null, null);
  }

  /**
   * Constructs the upload component. The following defaults will be used:
   * <ul>
   * <li>runtimes: html5,flash,silverlight,html4</li>
   * <li>chunkSize: null</li>
   * <li>maxFileSize: 10MB</li>
   * <li>multiSelection: false</li>
   * </ul>
   *
   * @param caption the caption of the component
   * @param receiver the receiver to create the output stream to receive upload
   * data
   */
  public Plupload(String caption, Upload.Receiver receiver) {
    registerRpc(rpc);
    setCaption(caption);

    // Add the Silverlight mime-type if it isn't already in the resolver.
    if (FileTypeResolver.DEFAULT_MIME_TYPE.equals(FileTypeResolver.getMIMEType(
        "Moxie.xap"))) {
      FileTypeResolver.addExtension("xap", "application/x-silverlight-app");
    }

    setResource("flashSwfUrl", new ClassResource(getClass(),
        "plupload/Moxie.swf"));
    setResource("silverlightSwfUrl", new ClassResource(getClass(),
        "plupload/Moxie.xap"));

    setRuntimes(Runtime.HTML5, Runtime.FLASH, Runtime.SILVERLIGHT, Runtime.HTML4);
    setMaxFileSize(10 * 1024 * 1024L);
    getState().multiSelection = false;
    setReceiver(receiver);
  }

  @Override
  public void attach() {
    super.attach();

    // Get the URL for the stream variable which will also register
    // it in the connector tracker.
    String url = getSession().getCommunicationManager().
        getStreamVariableTargetUrl(this, "plupload", getStreamVariable());

    getState().url = url;
  }

  @Override
  public void detach() {
    // Cleanup our stream variable.
    getUI().getConnectorTracker().cleanStreamVariable(getConnectorId(),
        "plupload");

    super.detach();
  }

  /**
   * Fires the upload success event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   */
  protected void fireUploadSuccess(String filename, String mimeType,
      long length) {
    fireEvent(new SucceededEvent(this, filename, mimeType, length));
  }

  /**
   * Fires the the progress event to all registered listeners.
   *
   * @param totalBytes bytes received so far
   * @param contentLength actual size of the file being uploaded, if known
   *
   */
  protected void fireUpdateProgress(long totalBytes, long contentLength) {

    // This may be a progress event from a single chunk. We can ignore this
    // if we know we're going to have multiple chunks. If is a single chunk,
    // the content length may be greater than the overall content length
    // because of the extra fields sent in the multi-part request.
    if (uploadSession.contentLength == contentLength) {
      // this is implemented differently than other listeners to maintain
      // backwards compatibility
      if (progressListeners != null) {
        for (Upload.ProgressListener l : progressListeners) {
          l.updateProgress(totalBytes, uploadSession.contentLength);
        }
      }
    }
  }

  /**
   * Returns the stream variable that will receive the data events and content.
   *
   * @return the stream variable for this component
   */
  public StreamVariable getStreamVariable() {
    if (streamVariable == null) {
      streamVariable = new StreamVariableImpl();
    }

    return streamVariable;
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
  public void removeStartedListener(StartedListener listener) {
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
   * Fires the upload started event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   */
  protected void fireStarted(String filename, String mimeType) {
    fireEvent(new StartedEvent(this, filename, mimeType,
        uploadSession.contentLength, runtime));
  }

  /**
   * Fires the no input stream error event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   */
  protected void fireNoInputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoInputStreamEvent(this, filename, mimeType,
        length));
  }

  /**
   * Fires the no output stream error event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   */
  protected void fireNoOutputStream(String filename, String mimeType,
      long length) {
    fireEvent(new NoOutputStreamEvent(this, filename, mimeType,
        length));
  }

  /**
   * Fires the upload interrupted error event to all registered listeners.
   *
   * @param filename the name of the file provided by the client
   * @param mimeType the mime-type provided by the client
   * @param length the length/size of the file received
   * @param e the root exception
   */
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

  /**
   * Returns the maximum number of retries if an upload fails.
   *
   * @return the number of retries
   */
  public long getMaxRetries() {
    return getState().maxRetries;
  }

  /**
   * Sets the maximum number of retries if an upload fails.
   *
   * @param maxRetries the number of retries
   */
  public void setMaxRetries(int maxRetries) {
    getState().maxRetries = maxRetries;
  }

  /**
   * Returns the number of bytes read since the upload started. This value is
   * cleared after a successful upload.
   *
   * @return the number of bytes read
   */
  public long getBytesRead() {
    return uploadSession.bytesRead;
  }

  /**
   * Returns the receiver that will be used to create output streams when a file
   * starts uploading.
   *
   * @return the receiver for all incoming data
   */
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
   * server. Not all runtimes support chunking. If set to 0, chunking will be
   * disabled.
   *
   * @param size the size of each data chunk
   */
  public void setChunkSize(int size) {
    getState().chunkSize = size;
  }

  /**
   * Sets the size in bytes of each data chunk to be sent from the client to the
   * server.
   *
   * @return the size of each data chunk
   */
  public int getChunkSize() {
    return getState().chunkSize;
  }

  /**
   * Sets the size of the memory buffer used when retries are enabled. Incoming
   * data is first written to the buffer and committed to the receiver only when
   * the upload (or chunk) successfully completes. This allows the data to be
   * abandoned in the event of a partial upload and failure. This value should
   * always be equal to or greater than the chunk size or the buffer will not be
   * used.
   *
   * @param maxRetryBufferSize the buffer size in bytes
   */
  public void setMaxRetryBufferSize(int maxRetryBufferSize) {
    this.maxRetryBufferSize = maxRetryBufferSize;
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
    if (uploadSession != null) {
      throw new IllegalStateException("Uploading in progress.");
    }

    getState().submitUpload = true;
  }

  /**
   * Returns the size (i.e. reported content length) of the current upload. This
   * value may not be known and will be cleared after a successful upload.
   *
   * @return the upload size in bytes
   */
  public long getUploadSize() {
    return uploadSession == null ? -1 : uploadSession.contentLength;
  }

  /**
   * Interrupts the upload currently being received. The interruption will be
   * done by the receiving tread so this method will return immediately and the
   * actual interrupt will happen a bit later.
   */
  public void interruptUpload() {
    if (uploadSession != null) {
      uploadSession.interrupted = true;
    }
  }

  /**
   * Go into upload state. Due to buffering of RPC calls by Vaadin, it is
   * possible that the upload could be started by the data stream or the RPC
   * call. It is safe to call this method multiple times and additional calls
   * will simply be ignored.
   *
   * Warning: this is an internal method used by the framework and should not be
   * used by user of the Upload component. Using it results in the Upload
   * component going in wrong state and not working. It is currently public
   * because it is used by another class.
   */
  private void startUpload() {
    if (uploadSession == null) {
      uploadSession = new UploadSession();
    }

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
    // Cleanup the receiver stream.
    if (uploadSession != null) {
      if (uploadSession.receiverOutstream != null) {
        tryClose(uploadSession.receiverOutstream);
      }

      uploadSession = null;
    }
//    uploading = false;
//    contentLength = -1;
//    bytesRead = 0;
//    filename = null;
//    mimeType = null;
//    interrupted = false;
    getState().interruptUpload = false;
  }

  /**
   * Returns true if an upload is currently in progress.
   *
   * @return the upload in progress
   */
  public boolean isUploading() {
    return uploadSession != null;
  }

  /**
   * Sets the list of runtimes that the uploader will attempt to use. It will
   * try to initialize each runtime in order if one fails it will move on to the
   * next one.
   *
   * @param runtimes the list of runtimes
   */
  public void setRuntimes(Runtime... runtimes) {
    String value = "";

    for (Runtime r : runtimes) {
      if (!value.isEmpty()) {
        value += ",";
      }
      value += r.name().toLowerCase();
    }

    getState().runtimes = value;
  }

  /**
   * Returns the list of runtimes that the uploader will attempt to use.
   *
   * @return the list of runtimes
   */
  public Runtime[] getRuntimes() {
    String[] runtimes = new String[0];
    if (getState().runtimes != null) {
      runtimes = getState().runtimes.split(",");
    }

    int i = 0;
    Runtime[] values = new Runtime[runtimes.length];
    for (String r : runtimes) {
      values[i++] = Runtime.valueOf(r.toUpperCase());
    }

    return values;
  }

  @Override
  protected PluploadState getState() {
    return (PluploadState) super.getState();
  }

  /**
   * The event fired when an upload completes, both success or failure.
   */
  public static class FinishedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long length;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public FinishedEvent(Component source, String filename, String mimeType,
        long length) {
      super(source);

      this.filename = filename;
      this.mimeType = mimeType;
      this.length = length;
    }

    /**
     * Returns the file name.
     *
     * @return the file name
     */
    public String getFilename() {
      return filename;
    }

    /**
     * Returns the mime-type.
     *
     * @return the mime-type
     */
    public String getMimeType() {
      return mimeType;
    }

    /**
     * Returns the content length in bytes.
     *
     * @return the length in bytes
     */
    public long getLength() {
      return length;
    }

  }

  /**
   * A failed event describing no input stream available for reading.
   */
  public static class NoInputStreamEvent extends FailedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public NoInputStreamEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  /**
   * A failed event describing no output stream available for reading.
   */
  public static class NoOutputStreamEvent extends FailedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public NoOutputStreamEvent(Component source, String filename,
        String mimeType, long length) {
      super(source, filename, mimeType, length, null);
    }
  }

  /**
   * A listener for finished events.
   */
  public interface FinishedListener {

    /**
     * Called when an upload finishes, either success or failure.
     *
     * @param evt the event describing the completion
     */
    void uploadFinished(FinishedEvent evt);
  }

  /**
   * An event describing an upload failure.
   */
  public static class FailedEvent extends FinishedEvent {

    private final Exception reason;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     * @param reason the root cause exception
     */
    public FailedEvent(Component source, String filename, String mimeType,
        long length, Exception reason) {
      super(source, filename, mimeType, length);
      this.reason = reason;
    }

    /**
     * Returns the root cause exception if available.
     *
     * @return the root exception
     */
    public Exception getReason() {
      return reason;
    }
  }

  /**
   * A listener for failed events.
   */
  public interface FailedListener {

    /**
     * Called when an upload fails.
     *
     * @param evt the event details
     */
    void uploadFailed(FailedEvent evt);
  }

  /**
   * An event describing the start of an upload.
   */
  public static class StartedEvent extends Component.Event {

    private final String filename;
    private final String mimeType;
    private final long contentLength;
    private final Runtime runtime;

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param contentLength the content length in bytes provided by the client
     * @param runtime the runtime selected on the client
     */
    public StartedEvent(Component source, String filename, String mimeType,
        long contentLength, Runtime runtime) {
      super(source);
      this.filename = filename;
      this.mimeType = mimeType;
      this.contentLength = contentLength;
      this.runtime = runtime;
    }

    /**
     * Returns the runtime that was selected by the uploader after
     * initialization on the client. This information is useful for debugging
     * but should have little impact on functionality.
     *
     * @return the active runtime or null if one has not been selected
     */
    public Runtime getRuntime() {
      return runtime;
    }

    /**
     * The file name provided by the client.
     *
     * @return the file name
     */
    public String getFilename() {
      return filename;
    }

    /**
     * The mime-type provided by the client.
     *
     * @return the mime-type
     */
    public String getMimeType() {
      return mimeType;
    }

    /**
     * The content length in bytes provided by the client.
     *
     * @return the content length
     */
    public long getContentLength() {
      return contentLength;
    }
  }

  /**
   * A listener that receives started events.
   */
  public interface StartedListener {

    /**
     * Called when the upload is started.
     *
     * @param evt the event details
     */
    void uploadStarted(StartedEvent evt);
  }

  /**
   * An event describing a successful upload.
   */
  public static class SucceededEvent extends FinishedEvent {

    /**
     * Constructs the event.
     *
     * @param source the source component
     * @param filename the name of the file provided by the client
     * @param mimeType the mime-type provided by the client
     * @param length the content length in bytes provided by the client
     */
    public SucceededEvent(Component source, String filename, String mimeType,
        long length) {
      super(source, filename, mimeType, length);
    }

  }

  /**
   * A listener that receives upload success events.
   */
  public interface SucceededListener {

    /**
     * Called when an upload is successful.
     *
     * @param evt the event details
     */
    void uploadSucceeded(SucceededEvent evt);
  }

  /**
   * The remote procedure call interface which allows calls from the client side
   * to the server. For the most part these methods map to the events generated
   * by the Plupload_orig JavaScript component.
   */
  private class PluploadServerRpcImpl implements PluploadServerRpc {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    @Override
    public void onError(PluploadError error) {
      log.info("Error on upload. Code: {} Message: {}", error.getCode(),
          error.getMessage());

      fireUploadInterrupted(uploadSession.filename, uploadSession.mimeType,
          uploadSession.contentLength,
          new RuntimeException(error.getMessage()));
    }

    @Override
    public void onStateChanged(int state) {
      if (state == 1 && uploadSession != null) {
        endUpload();
      }
    }

    @Override
    public void onUploadFile(String filename, long contentLength) {

      startUpload();

      if (contentLength != -1) {
        uploadSession.contentLength = contentLength;
      }
      uploadSession.filename = filename;

      log.info("Started upload of file {} with length {}.",
          filename, contentLength);

      fireStarted(filename, null);
    }

    @Override
    public void onFileUploaded(String filename, long contentLength) {

      // Ignore if the upload was interrupted because the content can't
      // be trusted.
      if (!uploadSession.interrupted) {
        log.info("Completed upload of file {} with length {}.", filename,
            contentLength);

      // Use bytesRead rather than the given contentLength because it is
        // unreliable. For example, HTML4 on IE8 will always send null/-1.
        fireUploadSuccess(filename, uploadSession.mimeType,
            uploadSession.bytesRead);
      }
    }

    @Override
    public void onProgress(int percent) {
      // Ignore. We want the call to refresh uploader state (i.e. polling)
      // but we don't care about the progress value.
    }

    @Override
    public void onInit(String runtime) {
      log.info("Uploader initialized with runtime {}", runtime);

      Plupload.this.runtime = Runtime.valueOf(runtime.toUpperCase());
    }
  }

  /**
   * The stream variable that maps the stream events to the upload component and
   * the configured data receiver.
   */
  private class StreamVariableImpl implements
      com.vaadin.server.StreamVariable {

    private TransactionalOutputStream txOutstream;
    private long chunkContentLength;

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
      return uploadSession == null ? false : uploadSession.interrupted;
    }

    @Override
    public OutputStream getOutputStream() {
      if (uploadSession.receiverOutstream == null) {
        uploadSession.receiverOutstream = receiver.receiveUpload(
            uploadSession.filename, uploadSession.mimeType);
      }

      // If retries are configured we need to write all incoming input into a
      // buffer so we can throw it away in the event of a failure.
      boolean retryEnabled = getState().maxRetries > 0 && maxRetryBufferSize > 0;

      if (retryEnabled && chunkContentLength <= maxRetryBufferSize) {
        if (txOutstream == null) {
          log.info("Constructing new retry buffer with capacity {}.",
              maxRetryBufferSize);
          txOutstream = new TransactionalOutputStream(maxRetryBufferSize,
              uploadSession.receiverOutstream);
        }

        txOutstream.rollback();
      }
      else if (retryEnabled) {
        log.warn("Retries are enabled but the content length {} is larger "
            + "than the maximum data buffer of {}. Duplicate data may be "
            + "written to the receiver in the event of a partial upload and "
            + "retry. Configure chunking to avoid this warning.",
            uploadSession.contentLength, maxRetryBufferSize);

        txOutstream = null;
      }
      else {
        txOutstream = null;
      }

      // We don't want to permit closing of the output stream because
      // we may have a chunked upload that we need to write to the
      // same output stream.
      return txOutstream != null ? txOutstream :
          new UncloseableOutputStream(uploadSession.receiverOutstream);
    }

    @Override
    public void streamingStarted(StreamVariable.StreamingStartEvent event) {

      startUpload();

      if (uploadSession.mimeType == null) {
        uploadSession.mimeType = event.getMimeType();
      }
      if (uploadSession.filename == null) {
        // Try to use the file name from the upload started RPC call which
        // will be correct. Otherwise fall back to the stream started event
        // even though it will most likely contain "blob".
        uploadSession.filename = event.getFileName();
      }
      if (uploadSession.contentLength < event.getContentLength()) {
        // Try to use the file name from the upload started RPC call which
        // will be correct (except for HTML4/IE8). Otherwise fall back to the
        // stream started event even though it may contain the size of just
        // a single blob.
        uploadSession.contentLength = event.getContentLength();
      }

      chunkContentLength = event.getContentLength();
    }

    @Override
    public void streamingFinished(StreamVariable.StreamingEndEvent event) {
      // Flush the retry stream if we are supporting retries.
      if (txOutstream != null) {
        tryCommit(txOutstream);
      }

      // Update the total bytes read. This is needed because this stream
      // may only be one of many chunks.
      uploadSession.bytesRead += event.getBytesReceived();
      fireUpdateProgress(uploadSession.bytesRead, uploadSession.contentLength);
    }

    @Override
    public void streamingFailed(StreamVariable.StreamingErrorEvent event) {
      Exception exception = event.getException();

      if (exception instanceof NoInputStreamException) {
        fireNoInputStream(uploadSession.filename,
            uploadSession.mimeType, uploadSession.contentLength);
      }
      else if (exception instanceof NoOutputStreamException) {
        fireNoOutputStream(uploadSession.filename,
            uploadSession.mimeType, uploadSession.contentLength);
      }
      else if (exception instanceof FileUploadHandler.UploadInterruptedException) {
        if (!getState().interruptUpload) {
          // Tell the uploader to stop sending chunks.
          getState().interruptUpload = true;

          fireUploadInterrupted(uploadSession.filename,
              uploadSession.mimeType, uploadSession.contentLength, exception);
        }
      }

      // Assume that we'll get an onError RPC call that we can use to 
      // cleanup resources.
    }
  }

  /**
   * The information related to a single upload session.
   */
  private static class UploadSession {

    OutputStream receiverOutstream;
    long contentLength;
    String filename;
    String mimeType;
    long bytesRead;
    boolean interrupted;
  }

  /**
   * The available client side runtimes.
   */
  public enum Runtime {

    HTML4,
    FLASH,
    SILVERLIGHT,
    HTML5
  }

}
