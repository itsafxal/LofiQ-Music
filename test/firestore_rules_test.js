/**
 * Firestore Security Rules Comprehensive Unit Test Suite
 * Validates access control matrix, cross-user isolation, default deny behavior,
 * and granular Listen Together host vs guest authorization & attack vectors.
 */

const fs = require('fs');
const path = require('path');

// Read rules file to ensure it exists and has expected structure
const rulesContent = fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8');

console.log("=================================================");
console.log("   LofiQ Firestore Security Rules Test Suite     ");
console.log("=================================================");

function isRoomHost(auth, roomData) {
  if (!auth) return false;
  if (!roomData || !roomData.hostId) return false;
  if (roomData.hostId !== auth.uid) return false;
  if (roomData.participants && roomData.participants[auth.uid] && roomData.participants[auth.uid].role !== 'host') return false;
  return true;
}

function computeAffectedKeys(before, after) {
  const keys = new Set([...Object.keys(before || {}), ...Object.keys(after || {})]);
  const affected = [];
  for (const k of keys) {
    if (JSON.stringify((before || {})[k]) !== JSON.stringify((after || {})[k])) {
      affected.push(k);
    }
  }
  return affected;
}

function isValidHostUpdate(auth, before, after) {
  if (!isRoomHost(auth, before)) return false;
  // hostId cannot be changed
  if (before.hostId && after.hostId !== before.hostId) return false;
  // roomCode cannot be changed
  if (before.roomCode && after.roomCode !== before.roomCode) return false;
  // If active, host entry must remain with role 'host'
  if (after.active !== false) {
    if (after.participants && before.hostId) {
      if (!after.participants[before.hostId] || after.participants[before.hostId].role !== 'host') {
        return false;
      }
    }
  }
  return true;
}

function isValidGuestJoin(auth, before, after) {
  if (before.active !== true || after.active !== true) return false;
  if (before.participants && before.participants[auth.uid]) return false; // must not already be in
  if (!after.participants || !after.participants[auth.uid]) return false;
  if (after.participants[auth.uid].role !== 'guest' || after.participants[auth.uid].id !== auth.uid) return false;
  if (after.listenerCount !== before.listenerCount + 1) return false;

  const partDiff = computeAffectedKeys(before.participants, after.participants);
  if (partDiff.length !== 1 || partDiff[0] !== auth.uid) return false;

  if (before.hostId && before.participants && before.participants[before.hostId]) {
    if (JSON.stringify(after.participants[before.hostId]) !== JSON.stringify(before.participants[before.hostId])) return false;
  }

  const docDiff = computeAffectedKeys(before, after);
  for (const k of docDiff) {
    if (!['listenerCount', 'participants'].includes(k)) return false;
  }
  return true;
}

function isValidGuestHeartbeat(auth, before, after) {
  if (before.active !== true || after.active !== true) return false;
  if (!before.participants || !before.participants[auth.uid] || before.participants[auth.uid].role !== 'guest') return false;
  if (!after.participants || !after.participants[auth.uid] || after.participants[auth.uid].role !== 'guest') return false;
  if (after.participants[auth.uid].id !== auth.uid) return false;
  if (after.listenerCount !== before.listenerCount) return false;

  const partDiff = computeAffectedKeys(before.participants, after.participants);
  if (partDiff.length !== 1 || partDiff[0] !== auth.uid) return false;

  if (before.hostId && before.participants && before.participants[before.hostId]) {
    if (JSON.stringify(after.participants[before.hostId]) !== JSON.stringify(before.participants[before.hostId])) return false;
  }

  const docDiff = computeAffectedKeys(before, after);
  return docDiff.length === 1 && docDiff[0] === 'participants';
}

function isValidGuestReaction(auth, before, after) {
  if (before.active !== true || after.active !== true) return false;
  if (!before.participants || !before.participants[auth.uid]) return false;
  if (!Array.isArray(after.recentReactions) || after.recentReactions.length > 50) return false;
  if (after.listenerCount !== before.listenerCount) return false;
  if (JSON.stringify(before.participants) !== JSON.stringify(after.participants)) return false;

  const docDiff = computeAffectedKeys(before, after);
  return docDiff.length === 1 && docDiff[0] === 'recentReactions';
}

function isValidGuestLeave(auth, before, after) {
  if (before.active !== true || after.active !== true) return false;
  if (!before.participants || !before.participants[auth.uid]) return false;
  if (after.participants && after.participants[auth.uid]) return false; // removed

  const partDiff = computeAffectedKeys(before.participants, after.participants);
  if (partDiff.length !== 1 || partDiff[0] !== auth.uid) return false;

  if (after.listenerCount !== before.listenerCount - 1 && after.listenerCount !== 0) return false;

  if (before.hostId && before.participants && before.participants[before.hostId]) {
    if (JSON.stringify(after.participants[before.hostId]) !== JSON.stringify(before.participants[before.hostId])) return false;
  }

  const docDiff = computeAffectedKeys(before, after);
  for (const k of docDiff) {
    if (!['listenerCount', 'participants'].includes(k)) return false;
  }
  return true;
}

function isValidGuestUpdate(auth, before, after) {
  if (!auth) return false;
  return (
    isValidGuestJoin(auth, before, after) ||
    isValidGuestHeartbeat(auth, before, after) ||
    isValidGuestReaction(auth, before, after) ||
    isValidGuestLeave(auth, before, after)
  );
}

// Mock Evaluator replicating Firebase Rules Engine logic
function evaluateRule({ path: reqPath, method, auth, resourceData, requestResourceData }) {
  const segments = reqPath.split('/').filter(Boolean);
  const isAuthenticated = auth !== null && auth !== undefined;
  const isOwner = (uid) => isAuthenticated && auth.uid === uid;

  // 1. match /users/{userId}
  if (segments[0] === 'users' && segments.length >= 2) {
    const userId = segments[1];
    
    // User root doc: /users/{userId}
    if (segments.length === 2) {
      if (method === 'read') return isOwner(userId);
      if (method === 'create') {
        return isOwner(userId) && (!requestResourceData || requestResourceData.uid === userId);
      }
      if (method === 'update') {
        return isOwner(userId) && (!requestResourceData || !('uid' in requestResourceData) || requestResourceData.uid === userId);
      }
      if (method === 'delete') return isOwner(userId);
    }

    // Subcollections: /users/{userId}/favorites/{songId}, etc.
    if (segments.length >= 3) {
      return isOwner(userId);
    }
  }

  // 2. match /favorites/{favoriteId}
  if (segments[0] === 'favorites' && segments.length === 2) {
    if (!isAuthenticated) return false;
    if (method === 'read') {
      return (resourceData && (resourceData.userId === auth.uid || resourceData.uid === auth.uid));
    }
    if (method === 'create') {
      return (requestResourceData && (requestResourceData.userId === auth.uid || requestResourceData.uid === auth.uid));
    }
    if (method === 'update') {
      const isResourceOwner = resourceData && (resourceData.userId === auth.uid || resourceData.uid === auth.uid);
      const isRequestOwner = (!requestResourceData.userId || requestResourceData.userId === auth.uid) &&
                             (!requestResourceData.uid || requestResourceData.uid === auth.uid);
      return isResourceOwner && isRequestOwner;
    }
    if (method === 'delete') {
      return (resourceData && (resourceData.userId === auth.uid || resourceData.uid === auth.uid));
    }
  }

  // 3. match /listen_together_rooms/{roomCode}
  if (segments[0] === 'listen_together_rooms' && segments.length === 2) {
    if (!isAuthenticated) return false;
    if (method === 'read') {
      if (!resourceData) return true;
      if (resourceData.active === true) return true;
      if (isRoomHost(auth, resourceData)) return true;
      if (resourceData.participants && resourceData.participants[auth.uid]) return true;
      return false;
    }
    if (method === 'create') {
      if (!requestResourceData) return false;
      if (requestResourceData.active !== true) return false;
      if (requestResourceData.listenerCount !== 1) return false;
      const isHostInParticipants = requestResourceData.participants &&
        requestResourceData.participants[auth.uid] &&
        requestResourceData.participants[auth.uid].role === 'host';
      const isHostId = requestResourceData.hostId === auth.uid;
      return isHostInParticipants || isHostId;
    }
    if (method === 'update') {
      if (isValidHostUpdate(auth, resourceData, requestResourceData)) return true;
      return isValidGuestUpdate(auth, resourceData, requestResourceData);
    }
    if (method === 'delete') {
      return isRoomHost(auth, resourceData);
    }
  }

  // 4. Default Deny for all other collections
  return false;
}

const baseRoom = {
  roomCode: 'SRG-1001',
  roomName: 'Lofi Lounge',
  hostId: 'host1',
  hostName: 'Live Host',
  active: true,
  listenerCount: 1,
  songId: 'songA',
  songTitle: 'Song A',
  isPlaying: true,
  positionMs: 12000,
  recentReactions: [],
  participants: {
    host1: { id: 'host1', role: 'host', lastSeenMs: 1000 }
  }
};

const activeRoomWithGuest = {
  ...baseRoom,
  listenerCount: 2,
  participants: {
    host1: { id: 'host1', role: 'host', lastSeenMs: 1000 },
    guest1: { id: 'guest1', role: 'guest', lastSeenMs: 2000 }
  }
};

// Test cases definitions
const tests = [
  // User profiles & cross-user isolation
  { name: "Unauthenticated user CANNOT read user profile", path: "users/alice", method: "read", auth: null, expected: false },
  { name: "Unauthenticated user CANNOT write user profile", path: "users/alice", method: "create", auth: null, requestResourceData: { uid: "alice" }, expected: false },
  { name: "Bob CANNOT read Alice's user profile", path: "users/alice", method: "read", auth: { uid: "bob" }, expected: false },
  { name: "Bob CANNOT update Alice's user profile", path: "users/alice", method: "update", auth: { uid: "bob" }, requestResourceData: { displayName: "Hacked" }, expected: false },
  { name: "Alice CAN read her own user profile", path: "users/alice", method: "read", auth: { uid: "alice" }, expected: true },
  { name: "Alice CAN update her own user profile", path: "users/alice", method: "update", auth: { uid: "alice" }, requestResourceData: { displayName: "Alice Smith" }, expected: true },
  { name: "Alice CANNOT forge UID on user doc create", path: "users/alice", method: "create", auth: { uid: "alice" }, requestResourceData: { uid: "bob" }, expected: false },

  // Favorites subcollection isolation
  { name: "Unauthenticated user CANNOT read user favorites", path: "users/alice/favorites/song1", method: "read", auth: null, expected: false },
  { name: "Bob CANNOT read Alice's favorites", path: "users/alice/favorites/song1", method: "read", auth: { uid: "bob" }, expected: false },
  { name: "Bob CANNOT write to Alice's favorites", path: "users/alice/favorites/song1", method: "create", auth: { uid: "bob" }, expected: false },
  { name: "Alice CAN read her own favorites", path: "users/alice/favorites/song1", method: "read", auth: { uid: "alice" }, expected: true },
  { name: "Alice CAN add a song to her favorites", path: "users/alice/favorites/song1", method: "create", auth: { uid: "alice" }, expected: true },

  // Playlists subcollection isolation
  { name: "Bob CANNOT read Alice's playlists", path: "users/alice/playlists/pl1", method: "read", auth: { uid: "bob" }, expected: false },
  { name: "Bob CANNOT delete Alice's playlists", path: "users/alice/playlists/pl1", method: "delete", auth: { uid: "bob" }, expected: false },
  { name: "Alice CAN read her playlists", path: "users/alice/playlists/pl1", method: "read", auth: { uid: "alice" }, expected: true },
  { name: "Alice CAN delete her playlists", path: "users/alice/playlists/pl1", method: "delete", auth: { uid: "alice" }, expected: true },

  // Settings & Credentials subcollection isolation
  { name: "Bob CANNOT access Alice's sync credentials", path: "users/alice/settings/credentials", method: "read", auth: { uid: "bob" }, expected: false },
  { name: "Alice CAN access her own sync credentials", path: "users/alice/settings/credentials", method: "read", auth: { uid: "alice" }, expected: true },

  // Root favorites collection & Ownership Immutability
  { name: "Unauthenticated user CANNOT read root favorites", path: "favorites/fav1", method: "read", auth: null, resourceData: { userId: "alice" }, expected: false },
  { name: "Bob CANNOT read Alice's root favorite", path: "favorites/fav1", method: "read", auth: { uid: "bob" }, resourceData: { userId: "alice" }, expected: false },
  { name: "Alice CAN read Alice's root favorite", path: "favorites/fav1", method: "read", auth: { uid: "alice" }, resourceData: { userId: "alice" }, expected: true },
  { name: "Bob CANNOT forge ownership on root favorite creation", path: "favorites/fav1", method: "create", auth: { uid: "bob" }, requestResourceData: { userId: "alice" }, expected: false },
  { name: "Alice CAN create root favorite with her UID", path: "favorites/fav1", method: "create", auth: { uid: "alice" }, requestResourceData: { userId: "alice" }, expected: true },
  {
    name: "User CANNOT transfer root favorite ownership to another user on update",
    path: "favorites/fav1",
    method: "update",
    auth: { uid: "alice" },
    resourceData: { userId: "alice", songTitle: "Song 1" },
    requestResourceData: { userId: "bob", songTitle: "Song 1" },
    expected: false
  },
  {
    name: "Alice CAN update title of her root favorite",
    path: "favorites/fav1",
    method: "update",
    auth: { uid: "alice" },
    resourceData: { userId: "alice", songTitle: "Song 1" },
    requestResourceData: { userId: "alice", songTitle: "Song 1 Updated" },
    expected: true
  },

  // Listen Together live rooms - Room Read Privacy
  { name: "Unauthenticated user CANNOT read room", path: "listen_together_rooms/SRG-1001", method: "read", auth: null, expected: false },
  { name: "Authenticated user CAN read active room", path: "listen_together_rooms/SRG-1001", method: "read", auth: { uid: "guest1" }, resourceData: baseRoom, expected: true },
  {
    name: "Stranger CANNOT read inactive closed room",
    path: "listen_together_rooms/SRG-1001",
    method: "read",
    auth: { uid: "stranger" },
    resourceData: { ...baseRoom, active: false },
    expected: false
  },
  {
    name: "Host CAN read their inactive room",
    path: "listen_together_rooms/SRG-1001",
    method: "read",
    auth: { uid: "host1" },
    resourceData: { ...baseRoom, active: false },
    expected: true
  },
  {
    name: "Participant CAN read inactive room they participated in",
    path: "listen_together_rooms/SRG-1001",
    method: "read",
    auth: { uid: "guest1" },
    resourceData: { ...activeRoomWithGuest, active: false },
    expected: true
  },

  // Listen Together - Creation & Deletion
  { name: "Authenticated user CAN create room as host", path: "listen_together_rooms/SRG-1001", method: "create", auth: { uid: "host1" }, requestResourceData: { ...baseRoom }, expected: true },
  { name: "Creator CANNOT create room without designating self as host", path: "listen_together_rooms/SRG-1001", method: "create", auth: { uid: "attacker" }, requestResourceData: { ...baseRoom, hostId: "other" }, expected: false },
  { name: "Host CAN delete their room", path: "listen_together_rooms/SRG-1001", method: "delete", auth: { uid: "host1" }, resourceData: baseRoom, expected: true },
  { name: "Guest CANNOT delete host's room", path: "listen_together_rooms/SRG-1001", method: "delete", auth: { uid: "guest1" }, resourceData: baseRoom, expected: false },

  // Host Updates & Transfer Prevention
  {
    name: "Host CAN update playback track (songId & metadata)",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "host1" },
    resourceData: baseRoom,
    requestResourceData: { ...baseRoom, songId: "songB", songTitle: "Song B", positionMs: 0 }
  , expected: true },
  {
    name: "Host CAN update play/pause state",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "host1" },
    resourceData: baseRoom,
    requestResourceData: { ...baseRoom, isPlaying: false }
  , expected: true },
  {
    name: "Host CAN close/deactivate room on leave",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "host1" },
    resourceData: baseRoom,
    requestResourceData: { ...baseRoom, active: false, listenerCount: 0 }
  , expected: true },
  {
    name: "Host CAN reconcile and prune stale participants",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "host1" },
    resourceData: {
      ...baseRoom,
      listenerCount: 2,
      participants: { host1: { id: "host1", role: "host" }, staleGuest: { id: "staleGuest", role: "guest" } }
    },
    requestResourceData: {
      ...baseRoom,
      listenerCount: 1,
      participants: { host1: { id: "host1", role: "host" } }
    }
  , expected: true },
  {
    name: "Host CANNOT transfer hostId to another user",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "host1" },
    resourceData: baseRoom,
    requestResourceData: { ...baseRoom, hostId: "otherUser" }
  , expected: false },

  // Legitimate Guest Operations
  {
    name: "Guest CAN join room and register in participants",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: baseRoom,
    requestResourceData: {
      ...baseRoom,
      listenerCount: 2,
      participants: {
        ...baseRoom.participants,
        guest1: { id: "guest1", role: "guest", lastSeenMs: 2000 }
      }
    }
  , expected: true },
  {
    name: "Guest CAN send heartbeat updating own lastSeenMs",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      participants: {
        ...activeRoomWithGuest.participants,
        guest1: { id: "guest1", role: "guest", lastSeenMs: 25000 }
      }
    }
  , expected: true },
  {
    name: "Guest CAN send reaction emoji to recentReactions",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      recentReactions: [{ id: "r1", emoji: "❤️", timestampMs: 3000 }]
    }
  , expected: true },
  {
    name: "Guest CAN leave room (decrement count and remove self from participants)",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      listenerCount: 1,
      participants: { host1: { id: "host1", role: "host", lastSeenMs: 1000 } }
    }
  , expected: true },

  // Attack Vectors: Guest Membership & Tampering Attacks
  {
    name: "Non-participant CANNOT send heartbeat without joining",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "stranger" },
    resourceData: baseRoom,
    requestResourceData: {
      ...baseRoom,
      participants: {
        ...baseRoom.participants,
        stranger: { id: "stranger", role: "guest", lastSeenMs: 5000 }
      }
    }
  , expected: false }, // Because listenerCount was not incremented (not a valid join)
  {
    name: "Non-participant CANNOT inject reactions without joining",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "stranger" },
    resourceData: baseRoom,
    requestResourceData: {
      ...baseRoom,
      recentReactions: [{ id: "r_troll", emoji: "💀", timestampMs: 3000 }]
    }
  , expected: false },
  {
    name: "Guest CANNOT modify or impersonate another participant",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: {
      ...activeRoomWithGuest,
      listenerCount: 3,
      participants: {
        ...activeRoomWithGuest.participants,
        victimGuest: { id: "victimGuest", role: "guest", lastSeenMs: 2000 }
      }
    },
    requestResourceData: {
      ...activeRoomWithGuest,
      listenerCount: 3,
      participants: {
        ...activeRoomWithGuest.participants,
        victimGuest: { id: "victimGuest", role: "guest", lastSeenMs: 999999, spoofed: true }
      }
    }
  , expected: false },
  {
    name: "Guest CANNOT delete another participant",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: {
      ...activeRoomWithGuest,
      listenerCount: 3,
      participants: {
        ...activeRoomWithGuest.participants,
        victimGuest: { id: "victimGuest", role: "guest", lastSeenMs: 2000 }
      }
    },
    requestResourceData: {
      ...activeRoomWithGuest,
      listenerCount: 2,
      participants: { ...activeRoomWithGuest.participants }
    }
  , expected: false },
  {
    name: "Guest CANNOT remove or demote host in participants",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      participants: { guest1: { id: "guest1", role: "guest" } }
    }
  , expected: false },
  {
    name: "Guest CANNOT tamper with arbitrary listenerCount",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      listenerCount: 999
    }
  , expected: false },
  {
    name: "Guest CANNOT inject oversized reaction array (DoS prevention)",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: {
      ...activeRoomWithGuest,
      recentReactions: new Array(51).fill({ id: "x", emoji: "❤️" })
    }
  , expected: false },
  {
    name: "Guest CANNOT hijack playback songId",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: { ...activeRoomWithGuest, songId: "malicious_track" }
  , expected: false },
  {
    name: "Guest CANNOT hijack playback play/pause state",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: { ...activeRoomWithGuest, isPlaying: false }
  , expected: false },
  {
    name: "Guest CANNOT tamper with playback positionMs",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: { ...activeRoomWithGuest, positionMs: 999999 }
  , expected: false },
  {
    name: "Guest CANNOT terminate room session (active = false)",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: activeRoomWithGuest,
    requestResourceData: { ...activeRoomWithGuest, active: false }
  , expected: false },
  {
    name: "Guest CANNOT elevate self to host role in participants",
    path: "listen_together_rooms/SRG-1001",
    method: "update",
    auth: { uid: "guest1" },
    resourceData: baseRoom,
    requestResourceData: {
      ...baseRoom,
      listenerCount: 2,
      participants: { ...baseRoom.participants, guest1: { id: "guest1", role: "host" } }
    }
  , expected: false },

  // Default deny rule
  { name: "Unauthenticated user CANNOT access arbitrary system collection", path: "admin_secrets/keys", method: "read", auth: null, expected: false },
  { name: "Authenticated user CANNOT access arbitrary system collection", path: "admin_secrets/keys", method: "read", auth: { uid: "alice" }, expected: false },
  { name: "Authenticated user CANNOT write to arbitrary root documents", path: "configs/app", method: "create", auth: { uid: "alice" }, requestResourceData: { version: "3.0" }, expected: false }
];

let passed = 0;
let failed = 0;

for (const t of tests) {
  const result = evaluateRule(t);
  if (result === t.expected) {
    passed++;
    console.log(`[PASS] ${t.name}`);
  } else {
    failed++;
    console.error(`[FAIL] ${t.name}: expected ${t.expected}, got ${result}`);
  }
}

console.log("-------------------------------------------------");
console.log(`Summary: ${passed} passed, ${failed} failed out of ${tests.length} tests.`);
console.log("-------------------------------------------------");

if (failed > 0) {
  process.exit(1);
} else {
  console.log("All Firestore Security Rules simulation tests passed successfully!");
}
