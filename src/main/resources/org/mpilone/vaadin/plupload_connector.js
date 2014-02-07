
/*
 * The entry point into the connector from the Vaadin framework.
 */
org_mpilone_vaadin_Plupload = function() {
	
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
		    browse_button : 'plupload_browse_button_' + connectorId,
		    container : 'plupload_container_' + connectorId,
		    //max_file_size : state.maxFileSize,
		    chunk_size: state.chunkSize,
            max_retries: state.maxRetries,
		    multi_selection: state.multiSelection,
		    url: uploadUrl,
		    flash_swf_url: flashSwfUrl,
		    silverlight_xap_url: silverlightXapUrl
		});
		
		uploader.bind('UploadFile', function(up, file) {
            console_log("Upload file: " + file.name);
			rpcProxy.onUploadFile(file.name, file.size);
		});

		uploader.bind('Error', function(up, error) {
			var output = '';
			for (property in error) {
			  output += property + ': ' + error[property]+'; ';
			}
			console_log(output);
            
            error.file = null;
			rpcProxy.onError(error);
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
	    
	    uploader.bind('FileUploaded', function(up, file) {
	    	console_log("FileUploaded: " + file.name);
	        rpcProxy.onFileUploaded(file.name, file.size);
	    });
	    
	    uploader.bind('Init', function(up) {
	    	console_log("Init: " + up.runtime);
            rpcProxy.onInit(up.runtime);
	    });

	    uploader.bind('PostInit', function(up) {
	    	console_log("PostInit: " + up.runtime);
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
		uploader.settings.multi_selection = state.multiSelection;
		//uploader.settings.max_file_size = state.maxFileSize;
		uploader.settings.chunk_size = state.chunkSize;
        submitBtn.value = state.buttonCaption;
        immediate = state.immediate;
        
        if (immediate && submitBtn.parentNode === container) {
          // Remove the submit button and file name input.
          container.removeChild(fileInput);
          container.removeChild(submitBtn);
          browseBtn.innerHTML = state.buttonCaption;
        }
        else if (!immediate && submitBtn.parentNode !== container) {
          // Add the submit button and file name input.
          container.appendChild(fileInput);
          container.appendChild(submitBtn);
          browseBtn.innerHTML = "Browse...";
        }
		
		// Check for browse enabled.
		if (state.enabled && submitBtn.disabled) {
			// This seems to be an undocumented feature.
			// Refer to http://www.plupload.com/punbb/viewtopic.php?id=1450
			uploader.trigger("DisableBrowse", false);
			enabled = true;
			browseBtn.className = "browse-button";
            submitBtn.className = "submit-button";
            submitBtn.disabled = false;
		}
		else if (!state.enabled && !submitBtn.disabled) {
			uploader.trigger("DisableBrowse", true);
			enabled = false;
			browseBtn.className = "browse-button browse-button-disabled";
            submitBtn.className = "submit-button submit-button-disabled";
            submitBtn.disabled = true;
		}
		
        // Refresh to make sure everything is positioned correctly.
        uploader.refresh();
        
		// Check for upload start state change.
		if (state.submitUpload && uploader.state === plupload.STOPPED) {
			console_log("Starting upload.");			
			uploader.start();
		}
	};
	
	// -----------------------
	// Init component
    console_log("Building container.");
	
	var container = document.createElement("div");
	container.setAttribute("id", "plupload_container_" + connectorId);
	container.className = "plupload";
	element.appendChild(container);
	
    var browseBtn = document.createElement("div");
	browseBtn.setAttribute("id", "plupload_browse_button_" + connectorId);
	browseBtn.className = "browse-button";
    browseBtn.innerHTML = "Browse...";
	container.appendChild(browseBtn);
    
	var fileInput = document.createElement("input");
	fileInput.setAttribute("id", "plupload_file_input_" + connectorId);
	fileInput.setAttribute("type", "text");
	fileInput.setAttribute("readonly", "true");
	fileInput.className = "file-input";
	container.appendChild(fileInput);
    
	var submitBtn = document.createElement("input");
	submitBtn.setAttribute("id", "plupload_submit_button_" + connectorId);
    submitBtn.setAttribute("type", "button");
	submitBtn.className = "submit-button";
    submitBtn.innerHTML = "Submit";
    submitBtn.onclick = function() {
      uploader.start();
    };
	container.appendChild(submitBtn);
};