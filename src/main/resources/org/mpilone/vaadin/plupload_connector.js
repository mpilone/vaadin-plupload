
/*
 * The entry point into the connector from the Vaadin framework.
 */
org_mpilone_vaadin_Plupload = function() {
	
    var BROWSE_BUTTON_CAPTION = "Choose File";
    var BUTTON_CLASSNAME = "v-button v-widget";
    var BROWSE_BUTTON_CLASSNAME = "plupload-browse " + BUTTON_CLASSNAME;
    var SUBMIT_BUTTON_CLASSNAME = "plupload-submit " + BUTTON_CLASSNAME;
    
	/*
	 *  The root HTML element that represents this component. 
	 */
	var element = this.getElement();
	
	/*
	 * The RPC proxy to the server side implementation.
	 */
	var rpcProxy = this.getRpcProxy();
	
    /*
     * The unique ID of the connector.
     */
    var connectorId = this.getConnectorId();
    
    /*
     * The uploader currently displayed.
     */
	var uploader;
    
    /*
     * The flag which indicates if the upload should be immidiate after 
     * file selection.
     */
    var immediate = false;
    
    var progressPercent = 0;
	
	/*
	 * Simple method for logging to the JS console if one is available.
	 */
	function console_log(msg) {
		if (window.console) {
			console.log(msg);
		}
	}
	
	/*
	 * Builds and returns a Plupload uploader component using 
	 * the given state information.
	 */
	function buildUploader(flashSwfUrl, silverlightXapUrl, uploadUrl, state) {
		console_log("Building uploader for connector " + connectorId);
				
		var uploader = new plupload.Uploader({
			runtimes: state.runtimes,
		    browse_button : browseBtn.root,
		    container : container,
		    max_file_size : state.maxFileSize,
		    chunk_size: state.chunkSize,
            max_retries: state.maxRetries,
		    multi_selection: false,
		    url: uploadUrl,
		    flash_swf_url: flashSwfUrl,
		    silverlight_xap_url: silverlightXapUrl
		});
		
		uploader.bind('UploadFile', function(up, file) {
            console_log("Upload file: " + file.name + " with size " + file.size);
            
            progressPercent = 0;
            
            // It appears that size may be null for HTML4 upload in IE8.
            var f = {
              name: file.name,
              size: file.size ? file.size : -1,
              type: file.type ? file.type : null
            };
			rpcProxy.onUploadFile(f);
		});

		uploader.bind('Error', function(up, error) {
			var output = '';
			for (property in error) {
			  output += property + ': ' + error[property]+'; ';
			}
			console_log(output);
            
            var e = {
              code: error.code,
              message: error.message,
              file: {
                name: error.file ? error.file.name : null,
                size: error.file ? error.file.size : -1,
                type: error.file ? error.file.type : null
              }
            };
            
			rpcProxy.onError(e);
		});
		
		uploader.bind('FilesAdded', function(up, files) {
			console_log("Files added: " + files[0].name);
            fileInput.value = files[0].name;
            
            if (immediate && uploader.state === plupload.STOPPED) {
              console_log("Starting immediately.");
              window.setTimeout( function() { uploader.start(); }, 200);
            }
		});
	
	    uploader.bind('UploadComplete', function(up, files) {
	    	console_log("Upload is complete");
            
            // Clear the queue.
            uploader.splice(0, files.length);
	    });
        
        uploader.bind('StateChanged', function(up) {
          console_log("StateChanged: " + up.state);
          rpcProxy.onStateChanged(up.state);
        });
	    
	    uploader.bind('FileUploaded', function(up, file) {
	    	console_log("FileUploaded: " + file.name);
            
            var f = {
              name: file.name,
              size: file.size ? file.size : -1,
              type: file.type ? file.type : null
            };
            
	        rpcProxy.onFileUploaded(f);
	    });
	    
	    uploader.bind('Init', function(up) {
	    	console_log("Init: " + up.runtime);
            rpcProxy.onInit(up.runtime);
	    });

	    uploader.bind('PostInit', function(up) {
	    	console_log("PostInit: " + up.runtime);
	    });
        
	    uploader.bind('UploadProgress', function(up, file) {
	    	console_log("UploadProgress: " + file.percent);
            
            // Throttle the progress events so we don't flood the RPC channel.
            if (file.percent - progressPercent > 5) {
               rpcProxy.onProgress(file.percent);
               progressPercent = file.percent;
            }
	    });
	    
	    uploader.bind('FilesRemoved', function(up, files) {
	    	console_log("Files removed: " + files[0].name);
            fileInput.value = "";
	    });
	    
		uploader.init();
	    
	    return uploader;
	}
	
    /*
     * Called when the state on the server side changes. If the state 
     * changes require a rebuild of the upload component, it will be 
     * destroyed and recreated. All other state changes will be applied 
     * to the existing upload instance.
     */
	this.onStateChange = function() {
		
		var state = this.getState();

		console_log("State change!");
		
		var uploadUrl = this.translateVaadinUri(state.url);
        var flashSwfUrl = this.translateVaadinUri(state.resources["flashSwfUrl"].uRL);
		var silverlightXapUrl = this.translateVaadinUri(state.resources["silverlightSwfUrl"].uRL);
		
		// Check for any state changes that require a complete rebuild of the uploader.
		var rebuild = uploader === undefined;
		//rebuild = rebuild || uploader.settings.url !== uploadUrl;
		rebuild = rebuild || uploader.settings.runtimes !== state.runtimes;
		
		// If we need to rebuild, destroy the current uploader and recreate it.
		if (rebuild) {
			if (uploader !== undefined) {
				uploader.destroy();
			}
			try {
				uploader = buildUploader(flashSwfUrl, silverlightXapUrl, 
                  uploadUrl, state);
			}
			catch (ex) {
				// TODO: This needs to be cleaned up!
                console_log(ex);
				alert(ex);
			}
		}
		
		// Apply state that doesn't require a rebuild.
		uploader.settings.max_file_size = state.maxFileSize;
		uploader.settings.chunk_size = state.chunkSize;
        submitBtn.caption.innerHTML = state.buttonCaption;
        immediate = state.immediate;
        
        if (immediate && submitBtn.root.parentNode === container) {
          // Remove the submit button and file name input.
          container.removeChild(fileInput);
          container.removeChild(submitBtn.root);
          browseBtn.caption.innerHTML = state.buttonCaption;
        }
        else if (!immediate && submitBtn.root.parentNode !== container) {
          // Add the submit button and file name input.
          container.appendChild(fileInput);
          container.appendChild(submitBtn.root);
          browseBtn.caption.innerHTML = BROWSE_BUTTON_CAPTION;
        }
		
		// Check for browse enabled.
		if (state.enabled && submitBtn.disabled) {
			uploader.disableBrowse(false);
			browseBtn.root.className = BROWSE_BUTTON_CLASSNAME;
            submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME;
            submitBtn.disabled = false;
		}
		else if (!state.enabled && !submitBtn.disabled) {
			uploader.disableBrowse(true);
			browseBtn.root.className = BROWSE_BUTTON_CLASSNAME + " v-disabled";
            submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME + " v-disabled";
            submitBtn.disabled = true;
		}
		
        // Refresh to make sure everything is positioned correctly.
        uploader.refresh();
        
		// Check for upload start state change.
		if (state.submitUpload && uploader.state === plupload.STOPPED) {
			console_log("Starting upload.");			
			uploader.start();
		}
        
		// Check for upload stop state change.
		if (state.interruptUpload && uploader.state === plupload.STARTED) {
			console_log("Aborting upload.");			
			uploader.stop();
		}
	};

    function createPseudoVaadinButton() {
      
      var btn = document.createElement("div");
	btn.setAttribute("role", "button");
	btn.className = BUTTON_CLASSNAME;
    
    var btnWrap = document.createElement("span");
    btnWrap.className = "v-button-wrap";
    btn.appendChild(btnWrap);
    
    var btnCaption = document.createElement("span");
    btnCaption.className = "v-button-caption";
    btnCaption.innerHTML = "Button";
    btnWrap.appendChild(btnCaption);
      
      return {
        root: btn,
        wrap: btnWrap,
        caption: btnCaption
      };
      
    }
  
	// -----------------------
	// Init component
    console_log("Building container.");
    
	var container = document.createElement("div");
	container.setAttribute("id", "plupload_container_" + connectorId);
	container.className = "plupload";
	element.appendChild(container);
	
    var browseBtn = createPseudoVaadinButton();
    browseBtn.root.setAttribute("id", "plupload_browse_button_" + connectorId);
	browseBtn.root.className = BROWSE_BUTTON_CLASSNAME;
    browseBtn.caption.innerHTML = "Choose File";
    container.appendChild(browseBtn.root);
    
	var fileInput = document.createElement("input");
	fileInput.setAttribute("type", "text");
	fileInput.setAttribute("readonly", "true");
	fileInput.className = "plupload-file v-textfield v-widget v-textfield-prompt v-readonly v-textfield-readonly";
	container.appendChild(fileInput);
    
	var submitBtn = createPseudoVaadinButton();
	submitBtn.root.className = SUBMIT_BUTTON_CLASSNAME;
    submitBtn.caption.innerHTML = "Submit";
    submitBtn.root.onclick = function() {
      uploader.start();
    };
    submitBtn.disabled = false;
	container.appendChild(submitBtn.root);
};