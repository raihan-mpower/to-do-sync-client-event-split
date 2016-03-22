/**
 * Copyright (c) 2015 Cloudant, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.cloudant.todo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.cloudant.sync.datastore.BasicDocumentRevision;
import com.cloudant.sync.datastore.ConflictException;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.DatastoreNotCreatedException;
import com.cloudant.sync.datastore.DocumentBodyFactory;
import com.cloudant.sync.datastore.DocumentException;
import com.cloudant.sync.datastore.DocumentRevision;
import com.cloudant.sync.datastore.MutableDocumentRevision;
import com.cloudant.sync.notifications.ReplicationCompleted;
import com.cloudant.sync.notifications.ReplicationErrored;
import com.cloudant.sync.query.IndexManager;
import com.cloudant.sync.query.QueryResult;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorBuilder;
import com.cloudant.todo.Jsonprocessor.ClientsProcessor;
import com.cloudant.todo.Jsonprocessor.EventsProcessor;
import com.cloudant.todo.Repositories.ClientsRepositories;
import com.cloudant.todo.Repositories.EventsRepositories;
import com.google.common.eventbus.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <p>Handles dealing with the datastore and replication.</p>
 */
class TasksModel {

    private static final String LOG_TAG = "TasksModel";

    private static final String DATASTORE_MANGER_DIR = "data";
    private static final String TASKS_DATASTORE_NAME = "tasks";

    private Datastore mDatastore;

    private Replicator mPushReplicator;
    private Replicator mPullReplicator;

    private final Context mContext;
    private final Handler mHandler;
    private TodoActivity mListener;

    public TasksModel(Context context) {

        this.mContext = context;

        // Set up our tasks datastore within its own folder in the applications
        // data directory.
        File path = this.mContext.getApplicationContext().getDir(
                DATASTORE_MANGER_DIR,
                Context.MODE_PRIVATE
        );
        DatastoreManager manager = new DatastoreManager(path.getAbsolutePath());
        try {
            this.mDatastore = manager.openDatastore(TASKS_DATASTORE_NAME);
        } catch (DatastoreNotCreatedException dnce) {
            Log.e(LOG_TAG, "Unable to open Datastore", dnce);
        }

        Log.d(LOG_TAG, "Set up database at " + path.getAbsolutePath());

        // Set up the replicator objects from the app's settings.
        try {
            this.reloadReplicationSettings();
        } catch (URISyntaxException e) {
            Log.e(LOG_TAG, "Unable to construct remote URI from configuration", e);
        }

        // Allow us to switch code called by the ReplicationListener into
        // the main thread so the UI can update safely.
        this.mHandler = new Handler(Looper.getMainLooper());

        Log.d(LOG_TAG, "TasksModel set up " + path.getAbsolutePath());
    }

    //
    // GETTERS AND SETTERS
    //

    /**
     * Sets the listener for replication callbacks as a weak reference.
     * @param listener {@link TodoActivity} to receive callbacks.
     */
    public void setReplicationListener(TodoActivity listener) {
        this.mListener = listener;
    }

    //
    // DOCUMENT CRUD
    //

    /**
     * Creates a task, assigning an ID.
     * @param task task to create
     * @return new revision of the document
     */
    public Task createDocument(Task task) {
        MutableDocumentRevision rev = new MutableDocumentRevision();
        rev.body = DocumentBodyFactory.create(task.asMap());
        try {
            BasicDocumentRevision created = this.mDatastore.createDocumentFromRevision(rev);
            return Task.fromRevision(created);
        } catch (DocumentException de) {
            return null;
        }
    }

    /**
     * Updates a Task document within the datastore.
     * @param task task to update
     * @return the updated revision of the Task
     * @throws ConflictException if the task passed in has a rev which doesn't
     *      match the current rev in the datastore.
     */
    public Task updateDocument(Task task) throws ConflictException {
        MutableDocumentRevision rev = task.getDocumentRevision().mutableCopy();
        rev.body = DocumentBodyFactory.create(task.asMap());
        try {
            BasicDocumentRevision updated = this.mDatastore.updateDocumentFromRevision(rev);
            return Task.fromRevision(updated);
        } catch (DocumentException de) {
            return null;
        }
    }

    /**
     * Deletes a Task document within the datastore.
     * @param task task to delete
     * @throws ConflictException if the task passed in has a rev which doesn't
     *      match the current rev in the datastore.
     */
    public void deleteDocument(Task task) throws ConflictException {
        this.mDatastore.deleteDocumentFromRevision(task.getDocumentRevision());
    }

    /**
     * <p>Returns all {@code Task} documents in the datastore.</p>
     */
    public List<Task> allTasks() {

        IndexManager im = new IndexManager(mDatastore);

        int nDocs = this.mDatastore.getDocumentCount();
//        List<BasicDocumentRevision> all = this.mDatastore.getAllDocuments(0, nDocs, true);
        List<Task> tasks = new ArrayList<Task>();
        if (im.isTextSearchEnabled()) {
            // Create a text index over the name and comment fields.
            String name = im.ensureIndexed(Arrays.<Object>asList("type"),"basic");
            if (name == null) {
                Log.v("TAG TAG", "error error big terror" );
                // there was an error creating the index
            }
        }
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("type", "Event");
        int querysize = im.find(query).size();
        Log.v("TAG TAG", "" + querysize);
        List<BasicDocumentRevision> all = mDatastore.getDocumentsWithIds(im.find(query).documentIds());

        // Filter all documents down to those of type Task.
        if(querysize>0) {
            for (BasicDocumentRevision rev : all) {

                EventsRepositories EventRepo = new EventsRepositories(mContext, "events", new String[]{"father_name", "voided", "providerId"});
                Log.v("Tag json", rev.getBody().toString());
                try {
                    EventsProcessor eventsProcessor = new EventsProcessor(new JSONObject(mContext.getResources().getString(R.string.jsonmapconfig2)), new JSONObject(rev.getBody().toString()));
                    EventRepo.insertValues(EventRepo.createValuesFor(eventsProcessor.createEventObject()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        testmethod();
        return tasks;
    }

    private void testmethod() {
        IndexManager im = new IndexManager(mDatastore);
        int nDocs = this.mDatastore.getDocumentCount();
//        List<BasicDocumentRevision> all = this.mDatastore.getAllDocuments(0, nDocs, true);

        if (im.isTextSearchEnabled()) {
            // Create a text index over the name and comment fields.
            String name = im.ensureIndexed(Arrays.<Object>asList("type"),"basic");
            if (name == null) {
                Log.v("TAG TAG", "error error big terror" );
                // there was an error creating the index
            }
        }
        Map<String, Object> query = new HashMap<String, Object>();
        query.put("type", "Client");
        int querysize = im.find(query).size();
        Log.v("TAG TAG", "" + querysize);
        List<BasicDocumentRevision> all = mDatastore.getDocumentsWithIds(im.find(query).documentIds());

        // Filter all documents down to those of type Task.
        if(querysize>0) {
            for (BasicDocumentRevision rev : all) {

                ClientsRepositories clientRepo = new ClientsRepositories(mContext, new String[]{"firstName"});
                Log.v("Tag json", rev.getBody().toString());
                try {
                    ClientsProcessor clientsProcessor = new ClientsProcessor(new JSONObject(mContext.getResources().getString(R.string.jsonClientMapConfig)), new JSONObject(rev.getBody().toString()));
                    clientRepo.insertValues(clientRepo.createValuesFor(clientsProcessor.createClientObject()));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //
    // MANAGE REPLICATIONS
    //

    /**
     * <p>Stops running replications.</p>
     *
     * <p>The stop() methods stops the replications asynchronously, see the
     * replicator docs for more information.</p>
     */
    public void stopAllReplications() {
        if (this.mPullReplicator != null) {
            this.mPullReplicator.stop();
        }
        if (this.mPushReplicator != null) {
            this.mPushReplicator.stop();
        }
    }

    /**
     * <p>Starts the configured push replication.</p>
     */
    public void startPushReplication() {
        if (this.mPushReplicator != null) {
            this.mPushReplicator.start();
        } else {
            throw new RuntimeException("Push replication not set up correctly");
        }
    }

    /**
     * <p>Starts the configured pull replication.</p>
     */
    public void startPullReplication() {
        if (this.mPullReplicator != null) {
            this.mPullReplicator.start();
        } else {
            throw new RuntimeException("Push replication not set up correctly");
        }
    }

    /**
     * <p>Stops running replications and reloads the replication settings from
     * the app's preferences.</p>
     */
    public void reloadReplicationSettings()
            throws URISyntaxException {

        // Stop running replications before reloading the replication
        // settings.
        // The stop() method instructs the replicator to stop ongoing
        // processes, and to stop making changes to the datastore. Therefore,
        // we don't clear the listeners because their complete() methods
        // still need to be called once the replications have stopped
        // for the UI to be updated correctly with any changes made before
        // the replication was stopped.
        this.stopAllReplications();

        // Set up the new replicator objects
        URI uri = this.createServerURI();

        mPullReplicator = ReplicatorBuilder.pull().to(mDatastore).from(uri).build();
        mPushReplicator = ReplicatorBuilder.push().from(mDatastore).to(uri).build();

        mPushReplicator.getEventBus().register(this);
        mPullReplicator.getEventBus().register(this);

        Log.d(LOG_TAG, "Set up replicators for URI:" + uri.toString());
    }

    /**
     * <p>Returns the URI for the remote database, based on the app's
     * configuration.</p>
     * @return the remote database's URI
     * @throws URISyntaxException if the settings give an invalid URI
     */
    private URI createServerURI()
            throws URISyntaxException {
        // We store this in plain text for the purposes of simple demonstration,
        // you might want to use something more secure.
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        String username = sharedPref.getString(TodoActivity.SETTINGS_CLOUDANT_USER, "");
        String dbName = sharedPref.getString(TodoActivity.SETTINGS_CLOUDANT_DB, "");
        String apiKey = sharedPref.getString(TodoActivity.SETTINGS_CLOUDANT_API_KEY, "");
        String apiSecret = sharedPref.getString(TodoActivity.SETTINGS_CLOUDANT_API_SECRET, "");
        String host = username + ".cloudant.com";
        String uriforconnect = "http://46.101.51.199:5984/opensrp_devtest";
        // We recommend always using HTTPS to talk to Cloudant.
        return new URI(uriforconnect);
    }

    //
    // REPLICATIONLISTENER IMPLEMENTATION
    //

    /**
     * Calls the TodoActivity's replicationComplete method don the main thread,
     * as the complete() callback will probably come from a replicator worker
     * thread.
     */
    @Subscribe
    public void complete(ReplicationCompleted rc) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.replicationComplete();
                }
            }
        });
    }

    /**
     * Calls the TodoActivity's replicationComplete method on the main thread,
     * as the error() callback will probably come from a replicator worker
     * thread.
     */
    @Subscribe
    public void error(ReplicationErrored re) {
        Log.e(LOG_TAG, "Replication error:", re.errorInfo.getException());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mListener != null) {
                    mListener.replicationError();
                }
            }
        });
    }
}
