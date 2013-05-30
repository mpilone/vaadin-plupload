
/*
 * The entry point into the connector from the Vaadin framework.
 */
org_prss_contentdepot_vaadin_Plupload = function() {
	
	/*
	 *  The root HTML element that represents this component. 
	 */
	var element = this.getElement();
	
	/*
	 * The RPC proxy to the server side implementation.
	 */
	var rpcProxy = this.getRpcProxy();
	
	/*
	 * Flag that indicates if the upload process has been started.
	 */
	var started = false;
	
	/*
	 * The flag that indicates if the browse button is 
	 * enabled or not in the uploader.
	 */
	var browseEnabled = true;
	
	/*
	 * The upload percentage of the current file. This is used as a work around to prevent
	 * duplicate percentage methods from going to the server because the @Delayed(lastOnly)
	 * annotation in the server RPC doesn't appear to be working in Vaadin 7.x for JavaScript 
	 * components. 
	 */
	var filePercent = 0;
	
	/*
	 * Simple method for logging to the JS console if one is available.
	 */
	function console_log(msg) {
		if (window.console) {
			console.log(msg);
		}
	}
	
	/*
	 * Clones all the fields in the Plupload File object and returns 
	 * a new File.
	 */
	function cloneFile(file) {
		var newFile = {};
    	newFile.id = file.id;
    	newFile.loaded = file.loaded;
    	newFile.name = file.name;
    	newFile.percent = file.percent;
    	newFile.size = file.size;
    	newFile.status = file.status;
    	
    	return newFile;
	}
	
	/*
	 * Builds and returns a Plupload uploader component using 
	 * the given state information.
	 */
	function buildUploader(state) {
		console_log("Building uploader.");
				
		var uploader = new plupload.Uploader({
			runtimes: state.runtimes,
		    browse_button : 'plupload_browse_button_' + state.instanceId,
		    container : 'plupload_container_' + state.instanceId,
		    max_file_size : state.maxFileSize,
		    chunk_size: state.chunkSize,
		    multi_selection: state.multiSelection,
		    url: state.url + '/upload',
		    flash_swf_url: state.url + '/flash',
		    silverlight_xap_url: state.url + '/silverlight'
		});
		
		uploader.bind('UploadFile', function(up, file) {
			rpcProxy.onUploadFile(file);
		});

		uploader.bind('Error', function(up, error) {
//			var output = '';
//			for (property in error) {
//			  output += property + ': ' + error[property]+'; ';
//			}
//			alert(output);

			rpcProxy.onError(error);
		});
		
		uploader.bind('FilesAdded', function(up, files) {
			console_log("Files added: " + files[0].name);
			rpcProxy.onFilesAdded(files);
		});
		
	    uploader.bind('UploadProgress', function(up, file) {
	    	console_log("file.percent: " + file.percent + ", filePercent: " + filePercent);

	    	// We clone the file because the percent upload will continue to change in 
	    	// the background and we don't want a race condition where it is changing 
	    	// while sitting in the Vaadin request dispatch queue.
	    	var newFile = cloneFile(file);
	    	
	    	// A hack to limit the number of server round trips 
	    	// on duplicate percentage reporting. See the field 
	    	// comments for more info.
	    	var makeRpcCall = newFile.percent == 0;
	    	makeRpcCall = makeRpcCall || newFile.percent != filePercent && newFile.percent == 100;
	    	makeRpcCall = makeRpcCall || (newFile.percent - filePercent) > 3;
	    	
	    	if (makeRpcCall) {
	    		rpcProxy.onUploadProgress(newFile);
	    		console_log(new Date() + " - Made onUploadProgress request: " + newFile.percent);
	    		filePercent = newFile.percent;
	    	}
	    });

	    uploader.bind('UploadComplete', function(up, files) {
	    	console_log("Upload is complete");
	    	rpcProxy.onUploadComplete(files);
	    });
	    
	    uploader.bind('FileUploaded', function(up, file) {
	    	console_log("FileUploaded: " + file.name);
	        rpcProxy.onFileUploaded(file);
	    });
	    
	    uploader.bind('Init', function(up) {
	    	console_log("Init: " + up.runtime);
	    	rpcProxy.onInit(up.runtime);
	    });

	    uploader.bind('PostInit', function(up) {
	    	console_log("PostInit: " + up.runtime);
//	    	rpcProxy.onInit(up.runtime);
	    });
	    
	    uploader.bind('FilesRemoved', function(up, files) {
	    	console_log("Files removed: " + files[0].name);
	    	rpcProxy.onFilesRemoved(files);
	    });
	    
	    try {
			uploader.init();
			}
			catch (ex) {
			  console_log(ex);
			}
	    
	    return uploader;
	}
	
	this.onStateChange = function() {
		
		var state = this.getState();
		var uploader = this.uploader;

		console_log("State change!");
		
		var uploadUrl = state.url + "/upload";
		var flashUrl = state.url + "/flash";
		var silverlightUrl = state.url + "/silverlight";
		
		// Check for any state changes that require a complete rebuild of the uploader.
		var rebuild = uploader == undefined;
		rebuild = rebuild || uploader.settings.url != uploadUrl;
		rebuild = rebuild || uploader.settings.flash_swf_url != flashUrl;
		rebuild = rebuild || uploader.settings.silverlight_xap_url != silverlightUrl;
		rebuild = rebuild || uploader.settings.runtimes != state.runtimes;
		
		
		// If we need to rebuild, destroy the current uploader and recreate it.
		if (rebuild) {
			if (uploader != undefined) {
				uploader.destroy();
			}
			try {
				this.uploader = buildUploader(state);
				uploader = this.uploader;
			}
			catch (ex) {
				// TODO: This needs to be cleaned up!
				alert(ex);
			}
		}
		
		// Apply state that doesn't require a rebuild.
		uploader.settings.multi_selection = state.multiSelection;
		uploader.settings.max_file_size = state.maxFileSize;
		uploader.settings.chunk_size = state.chunkSize;
		
		// Check for file removals.
		for (var i = 0; i < state.removedFileIds.length; ++i) {
			var deadFile = uploader.getFile(state.removedFileIds[i]);
			if (deadFile) {
				uploader.removeFile(deadFile);
			}
		}
		
		// Check for browse enabled.
		if (state.browseEnabled && !browseEnabled) {
			// This seems to be an undocumented feature.
			// Refer to http://www.plupload.com/punbb/viewtopic.php?id=1450
			uploader.trigger("DisableBrowse", false);
			browseEnabled = true;
			browseBtn.className = "browse-button";
		}
		else if (!state.browseEnabled && browseEnabled) {
			uploader.trigger("DisableBrowse", true);
			browseEnabled = false;
			browseBtn.className = "browse-button browse-button-disabled";
		}
		
		// Check for upload start state change.
		if (state.started && !started) {
			console_log("Starting upload.");			
			uploader.start();
		}
		started = state.started;
	}
	
	// -----------------------
	// Init component
    console_log("Building container.");
	
	var container = document.createElement("div");
	container.setAttribute("id", "plupload_container_" + this.getState().instanceId);
	container.className = "plupload";
	element.appendChild(container);
	
	var browseBtn = document.createElement("div");
	browseBtn.setAttribute("id", "plupload_browse_button_" + this.getState().instanceId);
	browseBtn.className = "browse-button";
	container.appendChild(browseBtn);

	var browseBtnTxt = document.createTextNode("Browse...");
	browseBtn.appendChild(browseBtnTxt);
}