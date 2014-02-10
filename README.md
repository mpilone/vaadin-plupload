# Vaadin Plupload

Vaadin wrapper for the [Plupload](http://www.plupload.com/) 
HTML5/Flash/Silverlight/HTML4 upload component. The component implements an API
that is extremely similar to the standard Vaadin Upload component so it
should require relatively few changes to swap between the implementations.

## Features
* Supports multiple client side runtimes (HTML5, Flash, Silverlight, and HTML4)
* Supports chunked uploading
* Supports immediate or manual upload initiation
* Modeled after the standard Upload component for server side compatibility
* The standard Vaadin FileUploadHandler is used for incoming data and 
  compatibility 

## Limitations
* By using the Upload component API and standard FileUploadHandler, some 
  features of Plupload are not exposed, such as the upload queue or maximum 
  file size
* Because Plupload announces the start of an upload via RPC, it is possible 
  that the data could begin arriving at the Receiver before the uploadStarted 
  event is fired
* The filename passed to the Receiver during output stream creation may be 
  inaccurate as Plupload labels chunks with a filename of "blob"

## Future Enhancements
* Better support for retries by not closing/flushing the input stream until the
  chunk is successful
* Support the maximum file size detection on the client side
  
This is a work in progress and it has not been thoroughly tested.
