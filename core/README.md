# PossumCore

A core library used by either the Awesome Possum Authenticate or the Awesome Possum Gather meant for gathering data, analysing it and authenticating the user of the phone.

This core library is not meant to be used as standalone, but is implemented of the sublibraries for actual use.

In order to include this core library into an extension, add the following to your app gradle dependencies:

    compile 'com.telenor:possumcore:1.3.0'

Note that it is not available for perusal quite yet - it will come to gradle and be available
very soon.

Remember to add jCenter() to your repositories.

To use these library components, here are the main points:

    BasicPossumCore possumCore = new BasicPossumCore(Context context, String uniqueUserId);

This creates a new instance with a given user identifier - note this identifier must be unique for the user.

    possumCore.startListening(); // Starts data gathering from sensors
    
This function starts gathering data from the sensors. It will automatically stop gathering after a set amount of time (default is 3 seconds). Should you wish to change this, use the function

    possumCore.setTimeOut(long timeOutInMillis);
    
This enables you to set a given timeout for all calls to startListening. Note: setting timeout to 0 would equal to infinite - use with caution unless you are sure you stop listening as well.

    possumCore.stopListening();
    
This method handles stopping all listening and should be called when you are done, regardless of whether the timeout would do it for you - as good practice.

Note that this library does nothing with the data at this point and will overwrite it for each call to startListening.
The PossumAuth and PossumGather handles what is done with the data after this point.

License
====================

    Copyright 2017 Telenor Digital AS

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
