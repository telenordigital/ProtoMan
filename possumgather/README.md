[![GitHub license](https://img.shields.io/github/license/telenordigital/awesome-possum.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
# PossumGather

An data gathering library using the PossumCore as basis meant for gathering data, analysing it and 
sending it to the cloud for storage and data model creation.

This library is used for the creation of better neural networks and is primarily meant to be used 
for helping the Awesome Possum project create better models for understanding and recognizing an 
individual based on the available sensors on a mobile phone.

In order to include this sdk, add the following to your app gradle dependencies:

    compile 'com.telenor:possumgather:1.3.0'

Note that it is not available for perusal quite yet - it will come to gradle and be available very 
soon.

Remember to add jCenter() to your repositories.

To use these library components, here are the main points:

    PossumGather possumGather = new PossumGather(Context context, String uniqueUserId);

This creates a new instance with a given user identifier - note this user identifier must be unique 
for the user.

    possumGather.startListening();
    
This function starts gathering data from the sensors. It will automatically stop gathering after a 
set amount of time (default is 10 minutes). Should you wish to change this, use the function

    possumGather.setTimeOut(long timeOutInMillis);
    
This enables you to set a given timeout for all calls to startListening. Note: setting timeout 
to 0 would equal to infinite - use with caution unless you are sure you stop listening as well. 
You should also limit it to a maximum of 10 minutes, as all data gathered is stored in memory until 
it is stopped.

If you do not want to let the timeout handle stopping, you can manually stop the gathering by 
calling the method

    possumGather.stopListening();
    
This method handles stopping all listening and should be called when you are done (at least when 
the app is closed by user), regardless of whether the timeout would do it for you - as good 
practice. It will zip all data gathered and store it in the app storage space for later upload. 
Note there are no blocking for "too much data stored", meaning you are responsible for either 
uploading the data or deleting it.

When you desire to send the data to the cloud for storage, use the

    possumGather.upload(Context context, String identityPoolId, String regionName, String bucket);
    
to upload all stored data to the cloud. All successfully sent will be deleted, so repeated calls to 
this method is possible.

Should the data gathered pile up and you are unable to upload, there are fail-saves in the methods

    possumGather.spaceUsed()
    
to determine how much space is used by the zipped files (should you desire to stop data gather over 
a certain amount) as well as

    possumGather.deleteStored()
    
for the absolute emergencies where you need to get rid of all data stored. Naturally this would be 
a shame, but it is handy to have the possibility.

Lastly, should your app utilize a form of camera (face-facing camera that is), the SDK would steal it from 
you if listening is called without care. In order to prevent you from losing control over camera,
use the following methods when you require undivided access to the camera

    possumGather.denyCamera()
    possumGather.allowCamera()
    
These methods will block the SDK's access to the camera (and allow it again). Note that unless both
are used, the camera will be blocked for the entire session the second denyCamera() is called 
unless a subsequent allowCamera() is called when the camera use is complete.

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
