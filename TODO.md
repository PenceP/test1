## TODO LIST - Not in order of priority

- Long press context menu is janky implementation, on my device the menu pops up and selects the first item before the user can even read them menu, and it disappears. need to ensure long press stays up and requires additional full down and up okay press before clicking again just because the user is still holding down "Okay"

- Scroll the link title on link selection page (horizontal scroll) since the link is always too long to fully display

- Remove the colored backdrop overlay on poster focus, just use standard transparent dark gray/black. This is using too many resources for not much gain.

- Fix Errors and warnings in Appendix A.

- Networks and Directors Rows Loading wrong icons occasionaly. Disney+ card is showing HBO Max icon. Hulu showing Netflix. Directors also show wrong images sometimes as well as Franchises rows showing wrong images. This could be related to the errors in Appendix A, if not, FIX THEM AS WELL.

- Networks, Directors, and Franchises rows do not currently update the backdrop based on the item selected. use a stretched out version of the selected card/poster for the backdrop and ensure it updates based on selectec item.

- Details page buttons: currently have Play, Trailer, Add to watchlist, Thumbs up, Thumbs down buttons. Lets change this to Play, Trailer, Mark watched (or unwatched if its already been watched - need to query our db for correct status. ensuring our db is always up to date with trakt.) and More (3 vertical dots as the icon with expanding "More" ) clicking more pops up a context box/modal attached to the more button with options Add to (or Remove From) Collection, Watchlist, Thumbs Up/Down. Actions that require trakt (buttons like watchlist,collection, mark watched, etc) should have a popup "Authorize Trakt to {Mark Watched}" if the user is not authenticated.

- Details Page for TV Shows: instead of focusing S1E1, We need to jump-to/auto-focus the "Next Up" or "Continue Watching" episode. Ex: if multiple episodes are in a half-watched state, choose the most recently watched. if all are fully watched go to the next up episode, if we are fully caught up then just go to the last episode. on that same note, bring in the same red progress bar from the HOME Continue Watching row. If fully watched then we do not display the red progress bar, only if we are "in progress" and watched percent is < 90% threshold. in regards to focusing the "next up" episode first, i want the Play button to also correctly display the correct S#E#. 

- In Settings -> Layout & Rows, next to Reset To Defaults button, I want an "Add From Liked Lists" button that displays a scrollable popup window of trakt liked lists we can add to the currently selected page. 

- In Settings -> Layout & Rows, another button on each row card (first item, before up arrow) that toggles between Portrait or Landscape layout for that row, updating with each click. this displays the current orientation for each row and saves in our configuration/db, whatever we're using. 

- Settings -> Playback: Add option to toggle Incremental Steps or Traditional Steps (when playing, stepping means hitting left or right on dpad). Traditional steps are 30s increments. Incremental Steps: [5,10,30,60,180,300,600] seconds, capping at 600s for each additional step. Kodi has a good implementation if you want to research. We should show the steps (ex: +30s) when playing videos. This will only work in our Custom Exoplayer implementation

- Exoplayer: the current implementation feels and looks dated. modern players have the buttons at the bottom of the screen, need to also have a subtitles button that shows available subtitles from https://github.com/a4k-openproject/a4kSubtitles (implement using a4kSubtitles as subtitle source). Also show imbedded subs at the top of the list if they exist in the file. Research modern exoplayer implementations and follow their designs.

- Settings->About: Show app name and version (using version.txt). Modern looking. We also need to rename the app STRMR. This means replace everything containing test1->STRMR, including filenames, directories, and all the text they contain. 

- Add clickable functionality (link to details page) for posters on movies and tv show pages, currently they only have a toast notification.








### Appendix A
## Error/Warnings 
2025-12-08 20:25:12.482 14870-14978 com.test1.tv            com.test1.tv                         I  AssetManager2(0xb4000078b8da8f38) locale list changing from [] to [en-US]
2025-12-08 20:25:12.489 14870-14978 GlideExecutor           com.test1.tv                         E  Request threw uncaught throwable (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
2025-12-08 20:25:12.508 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'Stranger Things S5E5', accentColor: #A81010
2025-12-08 20:25:12.527 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'Movie Collection': drawable://trakt2, cachedAccent=-6276944
2025-12-08 20:25:12.527 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'trakt2': 2131231249
2025-12-08 20:25:12.528 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable loaded for 'Movie Collection', cachedAccent=-6276944
2025-12-08 20:25:12.528 14870-14870 PosterAdapter           com.test1.tv                         D  Using cached accent for 'Movie Collection': #A038B0
2025-12-08 20:25:12.534 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'Movie Watchlist': drawable://ic_watchlist, cachedAccent=null
2025-12-08 20:25:12.534 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'ic_watchlist': 2131231080
2025-12-08 20:25:12.536 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'TV Show Collection': drawable://trakt2, cachedAccent=-6276944
2025-12-08 20:25:12.537 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'trakt2': 2131231249
2025-12-08 20:25:12.537 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable loaded for 'TV Show Collection', cachedAccent=-6276944
2025-12-08 20:25:12.537 14870-14870 PosterAdapter           com.test1.tv                         D  Using cached accent for 'TV Show Collection': #A038B0
2025-12-08 20:25:12.539 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'Stranger Things S5E5', accentColor: #A81010
2025-12-08 20:25:12.540 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.540 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.540 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.541 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.541 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.544 14870-14980 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:25:12.550 14870-14980 GlideExecutor           com.test1.tv                         E  Request threw uncaught throwable (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
2025-12-08 20:25:12.551 14870-14870 Glide                   com.test1.tv                         W  Load failed for [2131231080] with dimensions [600x338]
                                                                                                    class com.bumptech.glide.load.engine.GlideException: Failed to load resource
                                                                                                    There were 2 root causes:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException(Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                      Cause (1 of 3): class com.bumptech.glide.load.engine.GlideException: Failed LoadPath{AssetInputStream->Object->Drawable}, LOCAL
                                                                                                        Cause (1 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Drawable->Drawable}
                                                                                                        Cause (2 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->GifDrawable->Drawable}
                                                                                                        Cause (3 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Bitmap->BitmapDrawable}
                                                                                                        Cause (4 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->BitmapDrawable->Drawable}
                                                                                                      Cause (2 of 3): class com.bumptech.glide.load.engine.GlideException: Fetching data failed, class android.content.res.AssetFileDescriptor, LOCAL
                                                                                                    There was 1 root cause:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                        Cause (1 of 1): class android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                      Cause (3 of 3): class com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
2025-12-08 20:25:12.552 14870-14870 Glide                   com.test1.tv                         I  Root cause (1 of 2) (Ask Gemini)
                                                                                                    android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:407)
                                                                                                    	at android.content.res.Resources.openRawResourceFd(Resources.java:1436)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:104)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:92)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:225)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
                                                                                                    Caused by: java.io.FileNotFoundException: This file can not be opened as a file descriptor; it is probably compressed
                                                                                                    	at android.content.res.AssetManager.nativeOpenNonAssetFd(Native Method)
                                                                                                    	at android.content.res.AssetManager.openNonAssetFd(AssetManager.java:1167)
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:404)
                                                                                                    	... 22 more
2025-12-08 20:25:12.552 14870-14870 Glide                   com.test1.tv                         I  Root cause (2 of 2) (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
2025-12-08 20:25:12.558 14870-14870 Glide                   com.test1.tv                         W  Load failed for [2131231080] with dimensions [600x338]
                                                                                                    class com.bumptech.glide.load.engine.GlideException: Failed to load resource
                                                                                                    There were 2 root causes:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException(Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                      Cause (1 of 3): class com.bumptech.glide.load.engine.GlideException: Failed LoadPath{AssetInputStream->Object->Drawable}, LOCAL
                                                                                                        Cause (1 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Drawable->Drawable}
                                                                                                        Cause (2 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->GifDrawable->Drawable}
                                                                                                        Cause (3 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Bitmap->BitmapDrawable}
                                                                                                        Cause (4 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->BitmapDrawable->Drawable}
                                                                                                      Cause (2 of 3): class com.bumptech.glide.load.engine.GlideException: Fetching data failed, class android.content.res.AssetFileDescriptor, LOCAL
                                                                                                    There was 1 root cause:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                        Cause (1 of 1): class android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                      Cause (3 of 3): class com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
2025-12-08 20:25:12.558 14870-14870 Glide                   com.test1.tv                         I  Root cause (1 of 2) (Ask Gemini)
                                                                                                    android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:407)
                                                                                                    	at android.content.res.Resources.openRawResourceFd(Resources.java:1436)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:104)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:92)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:225)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
                                                                                                    Caused by: java.io.FileNotFoundException: This file can not be opened as a file descriptor; it is probably compressed
                                                                                                    	at android.content.res.AssetManager.nativeOpenNonAssetFd(Native Method)
                                                                                                    	at android.content.res.AssetManager.openNonAssetFd(AssetManager.java:1167)
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:404)
                                                                                                    	... 22 more
2025-12-08 20:25:12.558 14870-14870 Glide                   com.test1.tv                         I  Root cause (2 of 2) (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
2025-12-08 20:25:23.424 14796-14796 artd                    artd                                 I  GetBestInfo: odex next to the dex file (/data/app/~~ZurBN6lpDadSXwidc-PlHQ==/com.test1.tv-cYXUOlI6EvmelizV92m64Q==/oat/arm64/base.odex) is kOatUpToDate with filter 'speed-profile' executable 'false'
2025-12-08 20:25:25.945 14350-14394 katniss_se...ckageCache com.google.android.katniss:search    I  Apps for Intent { act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] }: [AppInfo(enabled=true, longVersionCode=1, packageName=com.android.tv.settings, versionCode=1, versionName=1.0), AppInfo(enabled=true, longVersionCode=84521718, packageName=com.android.vending, versionCode=84521718, versionName=45.2.17-31 [8] [PR] 732223966), AppInfo(enabled=true, longVersionCode=530317330, packageName=com.google.android.youtube.tv, versionCode=530317330, versionName=5.30.317), AppInfo(enabled=true, longVersionCode=618435470, packageName=com.google.android.play.games, versionCode=618435470, versionName=2025.05.61843 (809922015.809922015-040700)), AppInfo(enabled=true, longVersionCode=1, packageName=com.test1.tv, versionCode=1, versionName=1.0)]
2025-12-08 20:25:25.945 14350-14381 katniss_se...ckageCache com.google.android.katniss:search    I  Apps for Intent { act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] }: [AppInfo(enabled=true, longVersionCode=1, packageName=com.android.tv.settings, versionCode=1, versionName=1.0), AppInfo(enabled=true, longVersionCode=84521718, packageName=com.android.vending, versionCode=84521718, versionName=45.2.17-31 [8] [PR] 732223966), AppInfo(enabled=true, longVersionCode=530317330, packageName=com.google.android.youtube.tv, versionCode=530317330, versionName=5.30.317), AppInfo(enabled=true, longVersionCode=618435470, packageName=com.google.android.play.games, versionCode=618435470, versionName=2025.05.61843 (809922015.809922015-040700)), AppInfo(enabled=true, longVersionCode=1, packageName=com.test1.tv, versionCode=1, versionName=1.0)]
2025-12-08 20:25:25.946 14350-14393 katniss_se...ckageCache com.google.android.katniss:search    I  Apps for Intent { act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] }: [AppInfo(enabled=true, longVersionCode=1, packageName=com.android.tv.settings, versionCode=1, versionName=1.0), AppInfo(enabled=true, longVersionCode=84521718, packageName=com.android.vending, versionCode=84521718, versionName=45.2.17-31 [8] [PR] 732223966), AppInfo(enabled=true, longVersionCode=530317330, packageName=com.google.android.youtube.tv, versionCode=530317330, versionName=5.30.317), AppInfo(enabled=true, longVersionCode=618435470, packageName=com.google.android.play.games, versionCode=618435470, versionName=2025.05.61843 (809922015.809922015-040700)), AppInfo(enabled=true, longVersionCode=1, packageName=com.test1.tv, versionCode=1, versionName=1.0)]
2025-12-08 20:25:54.622 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'The Santa Clause 3: The Escape Clause', accentColor: #2080C0
2025-12-08 20:25:54.659 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:54.839 14870-14870 HomeFragment            com.test1.tv                         D  Updating hero section with: The Santa Clause 3: The Escape Clause
2025-12-08 20:25:54.853 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:54.880 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:54.890 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:54.895 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:54.910 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:25:58.830 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'Stranger Things S5E5', accentColor: #A81010
2025-12-08 20:25:58.983 14870-14870 HomeFragment            com.test1.tv                         D  Updating hero section with: Stranger Things S5E5
2025-12-08 20:26:01.470 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'The Santa Clause 3: The Escape Clause', accentColor: #2080C0
2025-12-08 20:26:01.622 14870-14870 HomeFragment            com.test1.tv                         D  Updating hero section with: The Santa Clause 3: The Escape Clause
2025-12-08 20:29:04.967 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'Movie Collection', accentColor: #A038B0
2025-12-08 20:29:05.128 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'Netflix': drawable://network_netflix, cachedAccent=null
2025-12-08 20:29:05.128 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'network_netflix': 2131231214
2025-12-08 20:29:05.133 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'Disney+': drawable://network_disney_plus, cachedAccent=null
2025-12-08 20:29:05.133 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'network_disney_plus': 2131231211
2025-12-08 20:29:05.135 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'HBO Max': drawable://network_hbo_max, cachedAccent=null
2025-12-08 20:29:05.135 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'network_hbo_max': 2131231212
2025-12-08 20:29:05.147 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:05.169 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:05.175 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:05.181 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable loaded for 'Netflix', cachedAccent=null
2025-12-08 20:29:05.181 14870-14870 PosterAdapter           com.test1.tv                         D  No cached accent, extracting for 'Netflix'
2025-12-08 20:29:05.182 14870-14870 HomeFragment            com.test1.tv                         D  Updating hero section with: Movie Collection
2025-12-08 20:29:05.204 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable loaded for 'Disney+', cachedAccent=null
2025-12-08 20:29:05.204 14870-14870 PosterAdapter           com.test1.tv                         D  No cached accent, extracting for 'Disney+'
2025-12-08 20:29:05.204 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable loaded for 'HBO Max', cachedAccent=null
2025-12-08 20:29:05.204 14870-14870 PosterAdapter           com.test1.tv                         D  No cached accent, extracting for 'HBO Max'
2025-12-08 20:29:05.229 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:05.235 14870-14961 PosterAdapter           com.test1.tv                         D  Extracted color for 'Disney+': #E0D8E0 (vibrant: false, darkVibrant: false, dominant: true)
2025-12-08 20:29:05.238 14870-14961 PosterAdapter           com.test1.tv                         D  Cached accent color for 'Disney+': #E0D8E0, isFocused: false
2025-12-08 20:29:05.242 14870-14902 PosterAdapter           com.test1.tv                         D  Extracted color for 'Netflix': #E80010 (vibrant: true, darkVibrant: true, dominant: true)
2025-12-08 20:29:05.242 14870-14902 PosterAdapter           com.test1.tv                         D  Cached accent color for 'Netflix': #E80010, isFocused: false
2025-12-08 20:29:05.243 14870-14928 PosterAdapter           com.test1.tv                         D  Extracted color for 'HBO Max': #E0D8E0 (vibrant: false, darkVibrant: false, dominant: true)
2025-12-08 20:29:05.243 14870-14928 PosterAdapter           com.test1.tv                         D  Cached accent color for 'HBO Max': #E0D8E0, isFocused: false
2025-12-08 20:29:05.264 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:05.268 14870-14971 HWUI                    com.test1.tv                         W  Image decoding logging dropped!
2025-12-08 20:29:23.966 14870-14870 PosterAdapter           com.test1.tv                         D  Item focused: 'Movie Watchlist', accentColor: #FFFFFF
2025-12-08 20:29:23.996 14870-14870 PosterAdapter           com.test1.tv                         D  Loading drawable resource for 'TV Show Watchlist': drawable://ic_watchlist, cachedAccent=null
2025-12-08 20:29:23.996 14870-14870 PosterAdapter           com.test1.tv                         D  Drawable ID for 'ic_watchlist': 2131231080
2025-12-08 20:29:23.999 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.000 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.000 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.000 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.000 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.001 14870-15029 HWUI                    com.test1.tv                         D  --- Failed to create image decoder with message 'unimplemented'
2025-12-08 20:29:24.005 14870-15029 GlideExecutor           com.test1.tv                         E  Request threw uncaught throwable (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
2025-12-08 20:29:24.006 14870-14870 Glide                   com.test1.tv                         W  Load failed for [2131231080] with dimensions [600x338]
                                                                                                    class com.bumptech.glide.load.engine.GlideException: Failed to load resource
                                                                                                    There were 2 root causes:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException(Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                      Cause (1 of 3): class com.bumptech.glide.load.engine.GlideException: Failed LoadPath{AssetInputStream->Object->Drawable}, LOCAL
                                                                                                        Cause (1 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Drawable->Drawable}
                                                                                                        Cause (2 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->GifDrawable->Drawable}
                                                                                                        Cause (3 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->Bitmap->BitmapDrawable}
                                                                                                        Cause (4 of 4): class com.bumptech.glide.load.engine.GlideException: Failed DecodePath{AssetInputStream->BitmapDrawable->Drawable}
                                                                                                      Cause (2 of 3): class com.bumptech.glide.load.engine.GlideException: Fetching data failed, class android.content.res.AssetFileDescriptor, LOCAL
                                                                                                    There was 1 root cause:
                                                                                                    android.content.res.Resources$NotFoundException(File res/Uh.xml from resource ID #0x7f080168)
                                                                                                     call GlideException#logRootCauses(String) for more detail
                                                                                                        Cause (1 of 1): class android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                      Cause (3 of 3): class com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
2025-12-08 20:29:24.007 14870-14870 Glide                   com.test1.tv                         I  Root cause (1 of 2) (Ask Gemini)
                                                                                                    android.content.res.Resources$NotFoundException: File res/Uh.xml from resource ID #0x7f080168
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:407)
                                                                                                    	at android.content.res.Resources.openRawResourceFd(Resources.java:1436)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:104)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$AssetFileDescriptorFactory.open(DirectResourceLoader.java:92)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:225)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)
                                                                                                    Caused by: java.io.FileNotFoundException: This file can not be opened as a file descriptor; it is probably compressed
                                                                                                    	at android.content.res.AssetManager.nativeOpenNonAssetFd(Native Method)
                                                                                                    	at android.content.res.AssetManager.openNonAssetFd(AssetManager.java:1167)
                                                                                                    	at android.content.res.ResourcesImpl.openRawResourceFd(ResourcesImpl.java:404)
                                                                                                    	... 22 more
2025-12-08 20:29:24.007 14870-14870 Glide                   com.test1.tv                         I  Root cause (2 of 2) (Ask Gemini)
                                                                                                    com.bumptech.glide.Registry$NoResultEncoderAvailableException: Failed to find result encoder for resource class: class android.graphics.drawable.VectorDrawable, you may need to consider registering a new Encoder for the requested type or DiskCacheStrategy.DATA/DiskCacheStrategy.NONE if caching your transformed resource is unnecessary.
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onResourceDecoded(DecodeJob.java:600)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob$DecodeCallback.onResourceDecoded(DecodeJob.java:642)
                                                                                                    	at com.bumptech.glide.load.engine.DecodePath.decode(DecodePath.java:60)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.loadWithExceptionList(LoadPath.java:76)
                                                                                                    	at com.bumptech.glide.load.engine.LoadPath.load(LoadPath.java:57)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runLoadPath(DecodeJob.java:539)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromFetcher(DecodeJob.java:503)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromData(DecodeJob.java:489)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:434)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherFailed(DecodeJob.java:416)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onLoadFailedInternal(SourceGenerator.java:223)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onLoadFailed(SourceGenerator.java:108)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:228)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.decodeFromRetrievedData(DecodeJob.java:442)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.onDataFetcherReady(DecodeJob.java:399)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.onDataReadyInternal(SourceGenerator.java:211)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator$1.onDataReady(SourceGenerator.java:101)
                                                                                                    	at com.bumptech.glide.load.model.DirectResourceLoader$ResourceDataFetcher.loadData(DirectResourceLoader.java:226)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNextLoad(SourceGenerator.java:95)
                                                                                                    	at com.bumptech.glide.load.engine.SourceGenerator.startNext(SourceGenerator.java:88)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runGenerators(DecodeJob.java:311)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.runWrapped(DecodeJob.java:280)
                                                                                                    	at com.bumptech.glide.load.engine.DecodeJob.run(DecodeJob.java:235)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)
                                                                                                    	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultThreadFactory$1.run(GlideExecutor.java:424)
                                                                                                    	at java.lang.Thread.run(Thread.java:1119)
                                                                                                    	at com.bumptech.glide.load.engine.executor.GlideExecutor$DefaultPriorityThreadFactory$1.run(GlideExecutor.java:383)