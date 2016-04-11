## This directory contains generated code.

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
   * First, download the discovery document
     * Navigate to the [discovery document][1] in browser
     * Save it to the local file system, e.g `$HOME/tmp/servcon_discovery.json`
     * _Using wget or curl should work, actually returns a 404_

   * _Temporary Workaround_: Edit the discovery document to remove an element that
     that does not generated compilable code.
     * Remove this code in this diff:
     ```sh
     2333a2334,2346
     >     "$.xgafv": {
     >       "enum": [
     >         "1",
     >         "2"
     >       ],
     >       "description": "V1 error format.",
     >       "enumDescriptions": [
     >         "v1 error format",
     >         "v2 error format"
     >       ],
     >       "location": "query",
     >       "type": "string"
     >     },
     ```

    * Run the following commands

     ```sh                                                                                                                                                                         TARGET_DIR=$HOME/tmp/gen-servcon/src/main/java
     mkdir -p $TARGET_DIR
     generate_library --input=$HOME/tmp/servcon_discovery.json \
     --language=java \
     --output_dir=$TARGET_DIR \
     --package_path=com/google/api/services
     ```

   * Finally, backup the current contents of src, and copy the re-generated code

     ```                                                                                                                                                                           mv src $HOME/tmp/servcon_src_backup
     mv $TARGET/src .
     ```

[1]: https://servicecontrol.googleapis.com/$discovery/rest?version=v1
[2]: https://github.com/google/apis-client-generator
[3]: https://github.com/google/apis-client-generator/issues/17
