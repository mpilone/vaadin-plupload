package org.mpilone.vaadin;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import javax.servlet.http.HttpServletRequest;

import org.mpilone.vaadin.shared.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.annotations.JavaScript;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinServletRequest;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.AbstractJavaScriptComponent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Upload;

/**
 * Wrapper for the Plupload HTML5/Flash/HTML4 upload component. You can find
 * more information at http://www.plupload.com/.
 * 
 * TODO: this class needs to be documented and requires some more functionality
 * (such as more listeners and file removal support).
 * 
 * @author mpilone
 */
@JavaScript({ "plupload_connector.js", "plupload/plupload.full.js" })
public class Plupload extends AbstractJavaScriptComponent {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * The log for this class.
   */
  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * A global counter used to generate unique IDs for each upload component to
   * prevent element collisions and to differentiate data requests.
   */
  private final static AtomicLong instanceCounter = new AtomicLong();

  /**
   * An event involving a single file modification.
   * 
   * @author mpilone
   */
  public static class SingleFileEvent extends Component.Event {
    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The file involved in the event.
     */
    private PluploadFile file;

    /**
     * Constructs the event with the given source and file.
     * 
     * @param source
     *          the source upload component
     * @param file
     *          the file involved in the event
     */
    public SingleFileEvent(Plupload source, PluploadFile file) {
      super(source);
      this.file = file;
    }

    /**
     * Returns the file involved in the event.
     * 
     * @return the file involved in the event
     */
    public PluploadFile getFile() {
      return file;
    }
  }

  /**
   * An event involving multiple file modifications.
   * 
   * @author mpilone
   */
  public static class MultipleFileEvent extends Component.Event {
    /**
     * Default serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The list of file involved in the event.
     */
    private List<PluploadFile> files;

    /**
     * Constructs the event with the given source and files.
     * 
     * @param source
     *          the source upload component
     * @param files
     *          the files involved in the event
     */
    public MultipleFileEvent(Plupload source, List<PluploadFile> files) {
      super(source);
      this.files = files;
    }

    /**
     * Returns the files involved in the event.
     * 
     * @return the files involved in the event
     */
    public List<PluploadFile> getFiles() {
      return files;
    }
  }

  /**
   * An error event from the upload component.
   * 
   * @author mpilone
   */
  public static class ErrorEvent extends Component.Event {
    /**
     * Default serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The message describing the event.
     */
    private String message;

    /**
     * Constructs the event with the given source and message.
     * 
     * @param source
     *          the source upload component
     * @param message
     *          the message describing the error
     */
    public ErrorEvent(Plupload source, String message) {
      super(source);
      this.message = message;
    }

    /**
     * Returns the message describing the error.
     * 
     * @return the message
     */
    public String getMessage() {
      return message;
    }
  }

  /**
   * Listener for the FilesAdded event from Plupload.
   * 
   * @author mpilone
   */
  public static interface FilesAddedListener {
    public void filesAdded(MultipleFileEvent evt);
  }

  /**
   * Listener for the FileUploaded event from Pluplaod.
   * 
   * @author mpilone
   */
  public static interface FileUploadedListener {
    public void fileUploaded(SingleFileEvent evt);
  }

  /**
   * Listener for the UploadComplete event from Pluplaod.
   * 
   * @author mpilone
   */
  public static interface UploadCompleteListener {
    public void uploadComplete(MultipleFileEvent evt);
  }

  /**
   * Listener for the UploadProgress event from Plupload.
   * 
   * @author mpilone
   */
  public static interface UploadProgressListener {
    public void uploadProgress(SingleFileEvent evt);
  }

  /**
   * The progress listener which will be registered with the request handler for
   * HTML4 runtimes. This enables progress reporting even when the runtime can't
   * provide it directly.
   */
  private PluploadRequestHandler.ProgressListener progressListener = new PluploadRequestHandler.ProgressListener() {
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.PluploadRequestHandler.ProgressListener#
     * percentChanged(int)
     */
    @Override
    public void percentChanged(int percent) {

      // log.debug("Upload percent changed: " + percent);

      // Synchronize with the UI thread because the progrss updates will come
      // from the request handler in a non-UI thread.
      Lock uiLock = VaadinSession.getCurrent().getLockInstance();
      uiLock.lock();
      try {
        if (uploadingFile != null) {
          uploadingFile.setPercent(percent);
        }
        rpc.onUploadProgress(uploadingFile);
      }
      finally {
        uiLock.unlock();
      }
    }
  };

  /**
   * The remote procedure call interface which allows calls from the client side
   * to the server. For the most part these methods map to the events generated
   * by the Plupload JavaScript component.
   */
  private PluploadServerRpc rpc = new PluploadServerRpc() {

    /**
     * Serialization ID.
     */
    private static final long serialVersionUID = 1L;

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onUploadFile(org
     * .prss.contentdepot.vaadin.shared.PluploadFile)
     */
    @Override
    public void onUploadFile(PluploadFile file) {
      log.debug("onUploadFile: " + file.getName());

      uploadingFile = file;

      // TODO add support for file upload listeners
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onFilesAdded(java
     * .util.List)
     */
    @Override
    public void onFilesAdded(List<PluploadFile> files) {
      for (PluploadFile file : files) {
        log.debug("onFilesAdded: " + file.getName());

        queuedFiles.put(file.getId(), file);
      }

      // If the user wanted a maximum number of files in the queue, enforce the
      // maximum.
      if (maxQueuedFiles > 0 && queuedFiles.size() > maxQueuedFiles) {
        // Make a copy of the list to avoid concurrent modification exceptions.
        List<PluploadFile> queuedFilesCopy = new ArrayList<PluploadFile>(
            queuedFiles.values());

        // Trim the list to just the ones we want to remove.
        queuedFilesCopy = queuedFilesCopy.subList(0, maxQueuedFiles);

        // Remove each of the files.
        for (Iterator<PluploadFile> iter = queuedFilesCopy.iterator(); iter
            .hasNext();) {
          PluploadFile file = iter.next();

          log.debug("Removing file because there is more than one file in the queue: "
              + file.getName());
          removeFile(file);
        }
      }

      filesAddedListeners
          .fireEvent(new MultipleFileEvent(Plupload.this, files));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onError(org.prss
     * .contentdepot.vaadin.shared.PluploadError)
     */
    @Override
    public void onError(PluploadError error) {
      log.debug("onError: [" + error.getCode() + "] " + error.getMessage());

      // TODO: implement error listeners
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onUploadProgress
     * (org.prss.contentdepot.vaadin.shared.PluploadFile)
     */
    @Override
    public void onUploadProgress(PluploadFile file) {
      log.debug("onUploadProgress: " + file.getPercent());

      // Update the uploading file so it has the most recent information.
      uploadingFile = file;

      uploadProgressListeners
          .fireEvent(new SingleFileEvent(Plupload.this, file));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onFileUploaded(
     * org.prss.contentdepot.vaadin.shared.PluploadFile)
     */
    @Override
    public void onFileUploaded(PluploadFile file) {
      log.debug("onFileUploaded: " + file.getName());

      // Remove the file from our queue now that it has been uploaded.
      queuedFiles.remove(file.getId());
      uploadingFile = null;

      fileUploadedListeners.fireEvent(new SingleFileEvent(Plupload.this, file));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onFilesRemoved(
     * java.util.List)
     */
    @Override
    public void onFilesRemoved(List<PluploadFile> files) {
      for (PluploadFile file : files) {
        log.debug("onFilesRemoved: " + file.getName());

        // Remove it from the list of files pending client side removal.
        getState().removedFileIds.remove(file.getId());
      }

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onInit(java.lang
     * .String)
     */
    @Override
    public void onInit(String runtime) {
      log.debug("onInit: " + runtime);

      // Clear the queue so we make sure our file list matches the client side
      // component.
      queuedFiles.clear();

      if (runtime.equalsIgnoreCase("html4")) {
        // Register a progress listener so we can still generate progress events
        // with HTML4 uploads.
        requestHandler.setProgressListener(progressListener);
      }
      else {
        // We assume that the client side runtime implementation can send
        // progress events via RPC calls from JavaScript.
        requestHandler.setProgressListener(null);
      }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.prss.contentdepot.vaadin.shared.PluploadServerRpc#onUploadComplete
     * (java.util.List)
     */
    public void onUploadComplete(java.util.List<PluploadFile> files) {
      log.debug("onUploadComplete: ");

      // Change the started state because the upload is complete.
      getState().started = false;

      // Fire the event to any listeners.
      uploadCompleteListeners.fireEvent(new MultipleFileEvent(Plupload.this,
          files));
    };
  };

  /**
   * The request handler which can return the Flash and Silverlight component as
   * well as handle the incoming data upload from the Plupload component.
   */
  private PluploadRequestHandler requestHandler;

  /**
   * The list of listeners interested in the upload progress event.
   */
  private AbstractListenable<UploadProgressListener, SingleFileEvent> uploadProgressListeners = new AbstractListenable<Plupload.UploadProgressListener, Plupload.SingleFileEvent>() {
    @Override
    protected void fireEvent(UploadProgressListener listener,
        SingleFileEvent event) {
      listener.uploadProgress(event);
    }
  };

  /**
   * The list of listeners interested in the files added event.
   */
  private AbstractListenable<FilesAddedListener, MultipleFileEvent> filesAddedListeners = new AbstractListenable<Plupload.FilesAddedListener, Plupload.MultipleFileEvent>() {
    @Override
    protected void fireEvent(FilesAddedListener listener,
        MultipleFileEvent event) {
      listener.filesAdded(event);
    }
  };

  /**
   * The list of listeners interested in the upload complete event.
   */
  private AbstractListenable<UploadCompleteListener, MultipleFileEvent> uploadCompleteListeners = new AbstractListenable<Plupload.UploadCompleteListener, Plupload.MultipleFileEvent>() {
    @Override
    protected void fireEvent(UploadCompleteListener listener,
        MultipleFileEvent event) {
      listener.uploadComplete(event);
    }
  };

  /**
   * The list of listeners interested in the file uploaded event.
   */
  private AbstractListenable<FileUploadedListener, SingleFileEvent> fileUploadedListeners = new AbstractListenable<Plupload.FileUploadedListener, Plupload.SingleFileEvent>() {
    @Override
    protected void fireEvent(FileUploadedListener listener,
        SingleFileEvent event) {
      listener.fileUploaded(event);
    }
  };

  /**
   * The map of files currently queued by file ID. A file will be removed from
   * the map when it has been uploaded or when it is manually removed. The map
   * is ordered with oldest first when iterating.
   */
  private Map<String, PluploadFile> queuedFiles;

  /**
   * The file that is currently uploading, if any.
   */
  private PluploadFile uploadingFile;

  /**
   * The maximum number of files that may be in the queue. If more files are
   * added, the oldest files in the queue will be removed. This is useful for
   * creating a single file upload component (i.e. a queue size of 1). This
   * method is provided for convenience as the behavior can be done manually
   * using {@link FilesAddedListener} and the {@link #removeFile(PluploadFile)}
   * method.
   */
  private int maxQueuedFiles = -1;

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

    this.queuedFiles = new LinkedHashMap<String, PluploadFile>();

    HttpServletRequest servletRequest = ((VaadinServletRequest) VaadinService
        .getCurrentRequest()).getHttpServletRequest();
    String vaadinServletPath = servletRequest.getContextPath()
        + servletRequest.getServletPath();

    getState().instanceId = instanceCounter.incrementAndGet();
    getState().url = vaadinServletPath + "/plupload/" + getState().instanceId
        + "-" + System.currentTimeMillis();
    getState().runtimes = "html5,flash,silverlight,html4";
    getState().chunkSize = null;
    getState().maxFileSize = 10 * 1024 * 1024L;
    getState().multiSelection = false;
    getState().removedFileIds = new ArrayList<String>();

    this.requestHandler = new PluploadRequestHandler(getState().url);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.vaadin.ui.AbstractComponent#attach()
   */
  @Override
  public void attach() {
    super.attach();

    log.debug("Adding request handler for Plupload data.");
    getSession().addRequestHandler(requestHandler);
  };

  /*
   * (non-Javadoc)
   * 
   * @see com.vaadin.ui.AbstractComponent#detach()
   */
  @Override
  public void detach() {
    log.debug("Removing request handler for Plupload data.");
    getSession().removeRequestHandler(requestHandler);

    super.detach();
  }

  /**
   * Starts the upload of any files in the upload queue. This method is the same
   * as calling {@link #start()} but it was added to be more consistent with the
   * standard Vaadin {@link Upload} component.
   */
  public void submitUpload() {
    start();
  }

  /**
   * Starts the upload of any files in the upload queue. Once started, the
   * uploads cannot be stopped until an error occurs or all the data is received
   * (this may change in the future).
   */
  public void start() {
    getState().started = true;
  }

  /**
   * Sets the size in bytes of each data chunk to be sent from the client to the
   * server. Not all runtimes support chunking. If set to null, chunking will be
   * disabled.
   * 
   * @param size
   *          the size of each data chunk
   */
  public void setChunkSize(Long size) {
    getState().chunkSize = size;
  }

  /**
   * Sets the maximum size in bytes of files that may be selected and uploaded.
   * 
   * @param size
   *          the maximum file size that may be uploaded
   */
  public void setMaxFileSize(long size) {
    getState().maxFileSize = size;
  }

  /**
   * Adds a listener to be notified of progress on a file upload.
   * 
   * @param listener
   *          the listener to be notified
   */
  public void addUploadProgressListener(UploadProgressListener listener) {
    uploadProgressListeners.addListener(listener);
  }

  /**
   * Adds a listener to be notified of upload complete events.
   * 
   * @param listener
   *          the listener to be notified
   */
  public void addUploadCompleteListener(UploadCompleteListener listener) {
    uploadCompleteListeners.addListener(listener);
  }

  /**
   * Adds a listener to be notified of files being added to the upload queue.
   * 
   * @param listener
   *          the listener to be notified
   */
  public void addFilesAddedListener(FilesAddedListener listener) {
    filesAddedListeners.addListener(listener);
  }

  /**
   * Adds a listener to be notified of a completed file upload.
   * 
   * @param listener
   *          the listener to be notified
   */
  public void addFileUploadedListener(FileUploadedListener listener) {
    fileUploadedListeners.addListener(listener);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.vaadin.ui.AbstractJavaScriptComponent#getState()
   */
  @Override
  protected PluploadState getState() {
    return (PluploadState) super.getState();
  }

  /**
   * 
   * Sets the flag which enables or disables multiple file selection when the
   * user is browsing for files. Note that event if multiple file selection is
   * disabled, the user may still add multiple files to the queue by selecting
   * one after another.
   * 
   * @param enabled
   *          true to enable, false to disable
   */
  public void setMultiSelection(boolean enabled) {
    getState().multiSelection = enabled;
  }

  public boolean isMultiSelect() {
    return getState().multiSelection;
  }

  /**
   * Sets the maximum number of files that may be in the queue. If more files
   * are added, the oldest files in the queue will be removed. This is useful
   * for creating a single file upload component (i.e. a queue size of 1). This
   * method is provided for convenience as the behavior can be done manually
   * using {@link FilesAddedListener} and the {@link #removeFile(PluploadFile)}
   * method. The default is -1 (no maximum).
   * 
   * @param maxQueuedFiles
   *          greater than 0 to enabled a queue maximum
   */
  public void setMaxQueuedFiles(int maxQueuedFiles) {
    this.maxQueuedFiles = maxQueuedFiles;
  }

  public int getMaxQueuedFiles() {
    return maxQueuedFiles;
  }

  /**
   * Returns the list of files currently queued for upload. The returned list is
   * unmodifiable. Use the {@link #removeFile(PluploadFile)} to remove a file
   * from the queue.
   * 
   * @return the list of files queued for upload
   */
  public List<PluploadFile> getQueuedFiles() {
    return Collections.unmodifiableList(new ArrayList<PluploadFile>(queuedFiles
        .values()));
  }

  /**
   * Removes the given file from the upload queue.
   * 
   * @param file
   *          the file to remove
   */
  public void removeFile(PluploadFile file) {
    if (queuedFiles.containsKey(file.getId())) {
      // Remove the file from the queued list.
      queuedFiles.remove(file.getId());

      // Add the file to the removed file list so it is cleaned up on the client
      // side.
      getState().removedFileIds.add(file.getId());
    }
  }

  /**
   * Enables or disables the browse button on the upload component. This method
   * should be preferred over {@link #setEnabled(boolean)} when an upload is
   * started or in-progress because disabling the entire upload component will
   * halt all upload activity. Normally an upload is started and the browse
   * button is disabled until the upload is complete to prevent the user from
   * adding more files while the queue upload is in progress.
   * 
   * @param enabled
   *          true to enable the button, false to disable
   */
  public void setBrowseEnabled(boolean enabled) {
    getState().browseEnabled = enabled;
  }

  public boolean isBrowseEnabled() {
    return getState().browseEnabled;
  }

  /**
   * Sets the receiver that will be used to create output streams when a file
   * starts uploading. The file data will be written to the returned stream. If
   * not set, the uploaded data will be ignored. The receiver may be called
   * multiple times with different file names if there are multiple files in the
   * upload queue.
   * 
   * @param receiver
   *          the receiver to use for creating file output streams
   */
  public void setReceiver(Upload.Receiver receiver) {
    requestHandler.setReceiver(receiver);
  }

}
