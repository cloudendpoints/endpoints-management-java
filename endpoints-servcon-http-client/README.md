## This directory contains semi-generated code.

### Overview
* The source code is generated from the service control [discovery document][1]
* The generator is [apis-client-generator tool][2]

### Re-generating the code

#### Prerequisites
* As of 2016/04, the working version of the [apis-client-generator tool][2] has _not_ been uploaded to pip
* So, to install a working version of [apis-client-generator tool][2]
   * clone the git repo
     * `git clone https://github.com/google/apis-client-generator`
   * run setup.py
     * `cd apis-client-generator`
     * `[sudo] python setup.py install`

#### Instructions
   1 First, download the discovery document
     * Navigate to the [discovery document][1] in browser
     * Save it to the local file system, e.g `$HOME/tmp/servcon_discovery.json`
     * _Using wget or curl should work, actually returns a 404_

   2 _Temporary Workaround_: Edit the discovery document to remove an element that
     that does not generated compilable code.

      * Remove the following json snippet:

      ```json
        "$.xgafv": {
          "enum": [
            "1",
            "2"
          ],
          "description": "V1 error format.",
          "enumDescriptions": [
            "v1 error format",
            "v2 error format"
          ],
          "location": "query",
          "type": "string"
        },
      ```

   3 Run the following commands

   ```sh
   TARGET_DIR=$HOME/tmp/gen-servcon/src/main/java
   mkdir -p $TARGET_DIR
   generate_library --input=$HOME/tmp/servcon_discovery.json \
   --language=java \
   --output_dir=$TARGET_DIR \
   --package_path=com/google/api/services
   ```


   4 Finally, backup the current contents of src, and copy the re-generated code

   ```
   mv src $HOME/tmp/servcon_src_backup
   mv $TARGET/src .
   ```

   5 Modify the generated files.
      1 ServicecontrolRequest.java
         1 Make the class ServicecontrolRequest<T> extend from AbstractGoogleClientRequest<T> instead of com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest<T>
         2 Change the type of Content in the constructor from Object to HttpContent.

      2 ServicecontrolRequestInitializer
         1 Make the class ServicecontrolRequestInitiliazer extend from CommonGoogleClientRequestInitializer instead of com.google.api.client.googleapis.services.json.CommonGoogleJsonClientRequestInitializer

         2 Change the method initializeJsonRequest.
            * FROM:  initializeJsonRequest(com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest<?> request)
            * TO: initialize(AbstractGoogleClientRequest<?> request)
            * Also update the super.initializeJsonRequest to be super.initialize()

      3 Servicecontrol.java

         1 Add the following imports

         ```java
         import com.google.api.client.googleapis.services.AbstractGoogleClient;
         import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
         import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
         ```

         2 Make the class ServiceControl.java extend AbstractGoogleClient instead of AbstractGoogleJsonClient

         3 Add the following member variable
            * private static final ObjectParser parser = new ProtoObjectParser();

         4 Remove the jsonFactory argument from the constructor and the Builder constructor
         5 Update the Builder class to extend AbstractGoogleClient.Builder instead of its JSON counterpart.
         6 Make sure the Builder constructor calls the appropriate parent constructor:
            * super(transport, DEFAULT_ROOT_URL, DEFAULT_SERVICE_PATH, parser, requestInitializer);
         7 import all the proto definitions from com.google.api.servicecontrol.v1
         8 For each request class, update the call to the the parent constructor by adding a parser for the content. For example
            * BEFORE: super(Servicecontrol.this, "POST", REST_PATH, content, CheckResponse.class);
            * AFTER: super(Servicecontrol.this, "POST", REST_PATH, new ProtoHttpContent(content), CheckResponse.class);





[1]: https://servicecontrol.googleapis.com/$discovery/rest?version=v1
[2]: https://github.com/google/apis-client-generator
[3]: https://github.com/google/apis-client-generator/issues/17
