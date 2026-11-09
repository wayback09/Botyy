import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * Fan-out new posts to followers' feeds
 */
export const onPostCreated = functions.firestore
  .document("posts/{postId}")
  .onCreate(async (snap, context) => {
    const post = snap.data();
    const postId = context.params.postId;
    const authorUid = post.authorUid;

    // Fetch author's followers
    const followsSnapshot = await db
      .collection("follows")
      .where("targetUid", "==", authorUid)
      .get();

    if (followsSnapshot.empty) {
      return null;
    }

    const batchSize = 400;
    let batch = db.batch();
    let count = 0;

    for (const doc of followsSnapshot.docs) {
      const followerUid = doc.data().followerUid;
      const feedRef = db.doc(`feeds/${followerUid}/items/${postId}`);
      batch.set(feedRef, {
        postId,
        authorUid,
        createdAt: post.createdAt || admin.firestore.FieldValue.serverTimestamp(),
      });
      count++;

      if (count % batchSize === 0) {
        await batch.commit();
        batch = db.batch();
      }
    }

    if (count % batchSize !== 0) {
      await batch.commit();
    }

    return null;
  });

/**
 * Maintain follower and following counts on follow created
 */
export const onFollowCreated = functions.firestore
  .document("follows/{followId}")
  .onCreate(async (snap) => {
    const follow = snap.data();
    const followerUid = follow.followerUid;
    const targetUid = follow.targetUid;

    const followerRef = db.doc(`users/${followerUid}`);
    const targetRef = db.doc(`users/${targetUid}`);

    const batch = db.batch();
    batch.update(followerRef, {
      followingCount: admin.firestore.FieldValue.increment(1),
    });
    batch.update(targetRef, {
      followersCount: admin.firestore.FieldValue.increment(1),
    });

    return batch.commit();
  });

/**
 * Maintain follower and following counts on follow deleted
 */
export const onFollowDeleted = functions.firestore
  .document("follows/{followId}")
  .onDelete(async (snap) => {
    const follow = snap.data();
    const followerUid = follow.followerUid;
    const targetUid = follow.targetUid;

    const followerRef = db.doc(`users/${followerUid}`);
    const targetRef = db.doc(`users/${targetUid}`);

    const batch = db.batch();
    batch.update(followerRef, {
      followingCount: admin.firestore.FieldValue.increment(-1),
    });
    batch.update(targetRef, {
      followersCount: admin.firestore.FieldValue.increment(-1),
    });

    return batch.commit();
  });

/**
 * Update conversation last-message on new message
 */
export const onMessageCreated = functions.firestore
  .document("conversations/{conversationId}/messages/{messageId}")
  .onCreate(async (snap, context) => {
    const message = snap.data();
    const conversationId = context.params.conversationId;

    const convoRef = db.doc(`conversations/${conversationId}`);
    return convoRef.update({
      lastMessageText: message.content || "[Media]",
      lastMessageSenderId: message.senderUid,
      lastMessageTimestamp: message.createdAt || admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  });

/**
 * Clean up feed items across fan-out timelines on post deletion
 */
export const onPostDeleted = functions.firestore
  .document("posts/{postId}")
  .onDelete(async (snap, context) => {
    const post = snap.data();
    const postId = context.params.postId;
    const authorUid = post.authorUid;

    const followsSnapshot = await db
      .collection("follows")
      .where("targetUid", "==", authorUid)
      .get();

    if (followsSnapshot.empty) {
      return null;
    }

    let batch = db.batch();
    let count = 0;

    for (const doc of followsSnapshot.docs) {
      const followerUid = doc.data().followerUid;
      const feedRef = db.doc(`feeds/${followerUid}/items/${postId}`);
      batch.delete(feedRef);
      count++;

      if (count % 400 === 0) {
        await batch.commit();
        batch = db.batch();
      }
    }

    if (count % 400 !== 0) {
      await batch.commit();
    }

    return null;
  });
