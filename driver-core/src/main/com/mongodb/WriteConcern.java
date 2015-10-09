/*
 * Copyright (c) 2008-2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// WriteConcern.java

package com.mongodb;

import com.mongodb.annotations.Immutable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.assertions.Assertions.isTrueArgument;
import static com.mongodb.assertions.Assertions.notNull;

/**
 * <p>Controls the acknowledgment of write operations with various options.</p>
 *
 * <p>{@code w}</p>
 * <ul>
 *  <li> 0: Don't wait for acknowledgement from the server </li>
 *  <li> 1: Wait for acknowledgement, but don't wait for secondaries to replicate</li>
 *  <li> &gt;=2: Wait for one or more secondaries to also acknowledge </li>
 *  <li> "majority": Wait for a majority of secondaries to also acknowledge </li>
 *  <li> "&lt;tag set name&gt;": Wait for one or moresecondaries to also acknowledge based on a tag set name</li>
 * </ul>
 * <p>{@code wtimeout} - how long to wait for secondaries to acknowledge before failing</p>
 * <ul>
 *   <li>0: indefinite </li>
 *   <li>&gt;0: time to wait in milliseconds</li>
 * </ul>
 *
 * <p>Other options:</p>
 * <ul>
 *   <li>{@code j}: If true block until write operations have been committed to the journal. Cannot be used in combination with
 *   {@code fsync}. Prior to MongoDB 2.6 this option was ignored if the server was running without journaling.  Starting with MongoDB 2.6
 *   write operations will fail with an exception if this option is used when the server is running without journaling.</li>
 * </ul>
 *
 * @mongodb.driver.manual reference/write-concern/ Write Concern
 */
@Immutable
public class WriteConcern implements Serializable {

    private static final long serialVersionUID = 1884671104750417011L;

    // map of the constants from above for use by fromString
    private static final Map<String, WriteConcern> NAMED_CONCERNS;

    private final Object w;

    private final int wtimeout;

    private final boolean fsync;

    private final boolean j;

    /**
     * Write operations that use this write concern will wait for acknowledgement from the primary server before returning, using the
     * default write concern configured on the server.
     *
     * @since 2.10.0
     */
    public static final WriteConcern ACKNOWLEDGED = new WriteConcern((Object) null, 0, false, false);

    /**
     * Write operations that use this write concern will wait for acknowledgement from a single member.
     *
     * @since 3.2
     */
    public static final WriteConcern W1 = new WriteConcern(1);

    /**
     * Write operations that use this write concern will wait for acknowledgement from two members.
     *
     * @since 3.2
     */
    public static final WriteConcern W2 = new WriteConcern(2);

    /**
     * Write operations that use this write concern will wait for acknowledgement from three members.
     *
     * @since 3.2
     */
    public static final WriteConcern W3 = new WriteConcern(3);


    /**
     * Write operations that use this write concern will return as soon as the message is written to the socket. Exceptions are raised for
     * network issues, but not server errors.
     *
     * @since 2.10.0
     */
    public static final WriteConcern UNACKNOWLEDGED = new WriteConcern(0);

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to flush the data to disk.
     * @deprecated Prefer WriteConcern#JOURNALED
     */
    @Deprecated
    public static final WriteConcern FSYNCED = new WriteConcern(true);

    /**
     * Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the journal
     * file on disk.
     */
    public static final WriteConcern JOURNALED = new WriteConcern(1, 0, false, true);

    /**
     * Exceptions are raised for network issues, and server errors; waits for at least 2 servers for the write operation.
     * @deprecated Prefer WriteConcern#W2
     */
    @Deprecated
    public static final WriteConcern REPLICA_ACKNOWLEDGED = new WriteConcern(2);

    /**
     * <p>Write operations that use this write concern will return as soon as the message is written to the socket. Exceptions are raised
     * for network issues, but not server errors.</p>
     *
     * <p>This field has been superseded by {@code WriteConcern.UNACKNOWLEDGED}, and may be deprecated in a future release.</p>
     *
     * @see WriteConcern#UNACKNOWLEDGED
     * @deprecated Prefer WriteConcern#UNACKNOWLEDGED
     */
    @Deprecated
    public static final WriteConcern NORMAL = UNACKNOWLEDGED;

    /**
     * <p>Write operations that use this write concern will wait for acknowledgement from the primary server before returning. Exceptions
     * are raised for network issues, and server errors.</p>
     *
     * <p>This field has been superseded by {@code WriteConcern.ACKNOWLEDGED}, and may be deprecated in a future release.</p>
     *
     * @see WriteConcern#ACKNOWLEDGED
     * @deprecated Prefer WriteConcern.ACKNOWLEDGED
     */
    @Deprecated
    public static final WriteConcern SAFE = ACKNOWLEDGED;

    /**
     * Exceptions are raised for network issues, and server errors; waits on a majority of servers for the write operation.
     */
    public static final WriteConcern MAJORITY = new WriteConcern("majority");

    /**
     * <p>Exceptions are raised for network issues, and server errors; the write operation waits for the server to flush the data to
     * disk.</p>
     *
     * <p>This field has been superseded by {@code WriteConcern.FSYNCED}, and may be deprecated in a future release.</p>
     *
     * @see WriteConcern#FSYNCED
     * @deprecated Prefer WriteConcern#JOURNALED
     */
    @Deprecated
    public static final WriteConcern FSYNC_SAFE = FSYNCED;

    /**
     * <p>Exceptions are raised for network issues, and server errors; the write operation waits for the server to group commit to the
     * journal file on disk. </p>
     *
     * <p>This field has been superseded by {@code WriteConcern.JOURNALED}, and may be deprecated in a future release.</p>
     *
     * @see WriteConcern#JOURNALED
     * @deprecated Prefer WriteConcern#JOURNALED
     */
    @Deprecated
    public static final WriteConcern JOURNAL_SAFE = JOURNALED;

    /**
     * <p>Exceptions are raised for network issues, and server errors; waits for at least 2 servers for the write operation.</p>
     *
     * <p>This field has been superseded by {@code WriteConcern.REPLICA_ACKNOWLEDGED}, and may be deprecated in a future release.</p>
     *
     * @see WriteConcern#W2
     * @deprecated Prefer WriteConcern#W2
     */
    @Deprecated
    public static final WriteConcern REPLICAS_SAFE = REPLICA_ACKNOWLEDGED;

    /**
     * Default constructor keeping all options as default.  Be careful using this constructor, as it's equivalent to {@code
     * WriteConcern.UNACKNOWLEDGED}, so writes may be lost without any errors being reported.
     *
     * @see WriteConcern#UNACKNOWLEDGED
     * @deprecated Prefer WriteConcen#UNACKNOWLEDGED
     */
    @Deprecated
    public WriteConcern() {
        this(0);
    }

    /**
     * Construct an instance with the given integer-based value for w
     *
     * @param w number of servers to ensure write propagation to before acknowledgment, which must be {@code >= 0}
     */
    public WriteConcern(final int w) {
        this(w, 0, false);
    }

    /**
     * Construct an instance with the given tag set-based value for w.
     *
     * @param w tag set, or "majority", representing the servers to ensure write propagation to before acknowledgment.  Do not use string
     *          representation of integer values for w
     * @mongodb.driver.manual tutorial/configure-replica-set-tag-sets/#replica-set-configuration-tag-sets Tag Sets
     */
    public WriteConcern(final String w) {
        this(w, 0, false, false);
    }

    /**
     * Calls {@link WriteConcern#WriteConcern(int, int, boolean)} with j=false
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     */
    public WriteConcern(final int w, final int wtimeout) {
        this(w, wtimeout, false);
    }

    /**
     * Calls {@link WriteConcern#WriteConcern(int, int, boolean)} with w=1 and wtimeout=0
     *
     * @param fsync whether or not to fsync
     * @deprecated prefer WriteConcern#JOURNALED or WriteConcern#withJ
     */
    @Deprecated
    public WriteConcern(final boolean fsync) {
        this(1, 0, fsync);
    }

    /**
     * <p>Creates a WriteConcern object.</p>
     *
     * <p>Specifies the number of servers to wait for on the write operation, and exception raising behavior </p>
     *
     * <p> {@code w} represents the number of servers:</p>
     * <ul>
     *     <li>{@code w=0} None, network socket errors raised</li>
     *     <li>{@code w=1} Checks server for errors as well as network socket errors raised</li>
     *     <li>{@code w>1} Checks servers (w) for errors as well as network socket errors raised</li>
     * </ul>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @deprecated Prefer WriteConcern#withW, WriteConcern#withWTimeout, WriteConcern#withJ
     */
    @Deprecated
    public WriteConcern(final int w, final int wtimeout, final boolean fsync) {
        this(w, wtimeout, fsync, false);
    }

    /**
     * <p>Creates a WriteConcern object.</p>
     *
     * <p>Specifies the number of servers to wait for on the write operation, and exception raising behavior</p>
     *
     * <p> {@code w} represents the number of servers:</p>
     * <ul>
     *     <li>{@code w=0} None, network socket errors raised</li>
     *     <li>{@code w=1} Checks server for errors as well as network socket errors raised</li>
     *     <li>{@code w>1} Checks servers (w) for errors as well as network socket errors raised</li>
     * </ul>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journaling group commit
     * @deprecated Prefer WriteConcern#withW, WriteConcern#withWTimeout, WriteConcern#withJ
     */
    @Deprecated
    public WriteConcern(final int w, final int wtimeout, final boolean fsync, final boolean j) {
        isTrueArgument("w >= 0", w >= 0);
        this.w = w;
        this.wtimeout = wtimeout;
        this.fsync = fsync;
        this.j = j;
    }

    /**
     * <p>Creates a WriteConcern object.</p>
     *
     * <p>Specifies the number of servers to wait for on the write operation, and exception raising behavior</p>
     *
     * <p> {@code w} represents the number of servers:</p>
     * <ul>
     *     <li>{@code w=0} None, network socket errors raised</li>
     *     <li>{@code w=1} Checks server for errors as well as network socket errors raised</li>
     *     <li>{@code w>1} Checks servers (w) for errors as well as network socket errors raised</li>
     * </ul>
     *
     * @param w        number of writes
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journaling group commit
     * @deprecated Prefer WriteConcern#withW, WriteConcern#withWTimeout, WriteConcern#withJ
     */
    @Deprecated
    public WriteConcern(final String w, final int wtimeout, final boolean fsync, final boolean j) {
        this((Object) notNull("w", w), wtimeout, fsync, j);
    }

    // Private constructor for creating the "default" unacknowledged write concern.  Necessary because there already a no-args
    // constructor that means something else.
    private WriteConcern(final Object w, final int wtimeout, final boolean fsync, final boolean j) {
        if (w == null) {
            isTrueArgument("wtimeout == 0", wtimeout == 0);
            isTrueArgument("fsync == false", !fsync);
            isTrueArgument("j == false", !j);
        }
        this.w = w;
        this.wtimeout = wtimeout;
        this.fsync = fsync;
        this.j = j;
    }

    /**
     * Gets the w value (the write strategy)
     *
     * @return w, either an instance of Integer or String or null
     */
    public Object getWObject() {
        return w;
    }

    /**
     * Gets the w parameter (the write strategy)
     *
     * @return w, as an int
     * @throws ClassCastException if w is not an integer
     */
    public int getW() {
        return (Integer) w;
    }

    /**
     * Gets the w parameter (the write strategy) in String format
     *
     * @return w as a string
     * @throws ClassCastException if w is not a String
     */
    public String getWString() {
        return (String) w;
    }

    /**
     * Gets the write timeout (in milliseconds)
     *
     * @return the timeout
     */
    public int getWtimeout() {
        return wtimeout;
    }

    /**
     * Gets the fsync flag (fsync to disk on the server)
     *
     * @return the fsync flag
     * @deprecated Prefer #getJ
     */
    @Deprecated
    public boolean getFsync() {
        return fsync();
    }

    /**
     * Gets the fsync flag (fsync to disk on the server)
     *
     * @return the fsync flag
     * @deprecated Prefer #getJ
     */
    @Deprecated
    public boolean fsync() {
        return fsync;
    }

    /**
     * Returns whether "getlasterror" should be called (w &gt; 0)
     *
     * @return whether this write concern will result in an an acknowledged write
     * @deprecated Prefer #isAcknowledged
     */
    @Deprecated
    public boolean callGetLastError() {
        return isAcknowledged();
    }

    /**
     * The server default is w == 1 and everything else the default value.
     *
     * @return true if this write concern is the server's default
     * @mongodb.driver.manual /reference/replica-configuration/#local.system.replset.settings.getLastErrorDefaults getLastErrorDefaults
     */
    public boolean isServerDefault() {
        return w == null;
    }

    /**
     * Gets this write concern as a document
     *
     * @return The write concern as a Document, even if {@code w &lt;= 0}
     */
    public BsonDocument asDocument() {
        BsonDocument document = new BsonDocument();

        addW(document);

        addWTimeout(document);
        addFSync(document);
        addJ(document);

        return document;
    }

    /**
     * Returns whether write operations should be acknowledged
     *
     * @return true w != null or w &gt; 0
     */
    public boolean isAcknowledged() {
        if (w instanceof Integer) {
            return (Integer) w > 0;
        }
        return true;
    }

    /**
     * Gets the WriteConcern constants by name (matching is done case insensitively).
     *
     * @param name the name of the WriteConcern
     * @return the {@code WriteConcern instance}
     */
    public static WriteConcern valueOf(final String name) {
        return NAMED_CONCERNS.get(name.toLowerCase());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        WriteConcern that = (WriteConcern) o;

        if (fsync != that.fsync) {
            return false;
        }
        if (j != that.j) {
            return false;
        }
        if (wtimeout != that.wtimeout) {
            return false;
        }
        if (w == null) {
            return that.w == null;
        }
        return w.equals(that.w);
    }

    @Override
    public int hashCode() {
        int result = w == null ? 0 : w.hashCode();
        result = 31 * result + wtimeout;
        result = 31 * result + (fsync ? 1 : 0);
        result = 31 * result + (j ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WriteConcern{w=" + w + ", wtimeout=" + wtimeout + ", fsync=" + fsync + ", j=" + j;

    }

    /**
     * Gets the j parameter (journal syncing)
     *
     * @return true if j is set
     */
    public boolean getJ() {
        return j;
    }

    /**
     * Constructs a new WriteConcern from the current one and the specified integer-based value for w
     *
     * @param w number of servers to ensure write propagation to before acknowledgment, which must be {@code >= 0}
     * @return the new WriteConcern
     */
    public WriteConcern withW(final int w) {
        return new WriteConcern(w, getWtimeout(), getFsync(), getJ());
    }

    /**
     * Constructs a new WriteConcern from the current one and the specified tag-set based value for w
     *
     * @param w tag set, or "majority", representing the servers to ensure write propagation to before acknowledgment.  Do not use string
     *          representation of integer values for w
     * @return the new WriteConcern
     * @see #withW(int)
     * @mongodb.driver.manual tutorial/configure-replica-set-tag-sets/#replica-set-configuration-tag-sets Tag Sets
     */
    public WriteConcern withW(final String w) {
        return new WriteConcern(w, getWtimeout(), getFsync(), getJ());
    }

    /**
     * Constructs a new WriteConcern from the current one and the specified fsync value
     *
     * @param fsync true if the write concern needs to include fsync
     * @return the new WriteConcern
     * @deprecated Prefer WriteConcern#withJ
     */
    @Deprecated
    public WriteConcern withFsync(final boolean fsync) {
        return new WriteConcern(getWObject() == null ? Integer.valueOf(1) : getWObject(), getWtimeout(), fsync, getJ());
    }

    /**
     * Constructs a new WriteConcern from the current one and the specified j value
     *
     * @param j true if journalling is required
     * @return the new WriteConcern
     */
    public WriteConcern withJ(final boolean j) {
        return new WriteConcern(getWObject() == null ? Integer.valueOf(1) : getWObject(), getWtimeout(), getFsync(), j);
    }

    /**
     * Constructs a new WriteConcern from the current one and the specified wtimeout
     *
     * @param wtimeout the wtimeout
     * @return the WriteConcern with the given wtimeout
     * @since 3.2
     */
    public WriteConcern withWTimeout(final int wtimeout) {
        return new WriteConcern(getWObject() == null ? Integer.valueOf(1) : getWObject(), wtimeout, getFsync(), getJ());

    }

    private void addW(final BsonDocument document) {
        if (w instanceof String) {
            document.put("w", new BsonString((String) w));
        } else if (w instanceof Integer){
            document.put("w", new BsonInt32((Integer) w));
        }
    }

    private void addJ(final BsonDocument document) {
        if (j) {
            document.put("j", BsonBoolean.TRUE);
        }
    }

    private void addFSync(final BsonDocument document) {
        if (fsync) {
            document.put("fsync", BsonBoolean.TRUE);
        }
    }

    private void addWTimeout(final BsonDocument document) {
        if (wtimeout > 0) {
            document.put("wtimeout", new BsonInt32(wtimeout));
        }
    }


    /**
     * Create a Majority Write Concern that requires a majority of servers to acknowledge the write.
     *
     * @param wtimeout timeout for write operation
     * @param fsync    whether or not to fsync
     * @param j        whether writes should wait for a journal group commit
     * @return Majority, a subclass of WriteConcern that represents the write concern requiring most servers to acknowledge the write
     * @deprecated Prefer WriteConcern#MAJORITY, WriteConcern#withWTimeout, WriteConcern#withJ
     */
    @Deprecated
    public static Majority majorityWriteConcern(final int wtimeout, final boolean fsync, final boolean j) {
        return new Majority(wtimeout, fsync, j);
    }

    /**
     * A write concern that blocks acknowledgement of a write operation until a majority of replica set members have applied it.
     * @deprecated Prefer WriteConcern#MAJORITY, WriteConcern#withWTimeout, WriteConcern#withJ
     */
    @Deprecated
    public static class Majority extends WriteConcern {

        private static final long serialVersionUID = -4128295115883875212L;

        /**
         * Create a new Majority WriteConcern.
         */
        public Majority() {
            this(0, false, false);
        }

        /**
         * Create a new WriteConcern with the given configuration.
         *
         * @param wtimeout timeout for write operation
         * @param fsync    whether or not to fsync
         * @param j        whether writes should wait for a journaling group commit
         */
        public Majority(final int wtimeout, final boolean fsync, final boolean j) {
            super("majority", wtimeout, fsync, j);
        }
    }

    static {
        NAMED_CONCERNS = new HashMap<String, WriteConcern>();
        for (final Field f : WriteConcern.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(WriteConcern.class)) {
                String key = f.getName().toLowerCase();
                try {
                    NAMED_CONCERNS.put(key, (WriteConcern) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
