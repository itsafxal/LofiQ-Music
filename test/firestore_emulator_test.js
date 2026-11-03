/**
 * Genuine Cloud Firestore Emulator Rules Unit Test Suite
 *
 * This test suite uses the official Google `@firebase/rules-unit-testing` framework.
 * It runs against the Firebase Local Emulator Suite:
 *   npx firebase-tools emulators:exec --only firestore "npm run test:emulator"
 */

const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { deleteField } = require('@firebase/firestore');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'lofiq-emulator-test';

describe('LofiQ Production Firestore Security Rules (Official Emulator)', () => {
  let testEnv;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, '../firestore.rules'), 'utf8'),
        host: '127.0.0.1',
        port: 8080
      }
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  beforeEach(async () => {
    if (testEnv) {
      await testEnv.clearFirestore();
    }
  });

  // =========================================================================
  // 1. User Profiles & Explicit Subcollections Isolation
  // =========================================================================
  test('Unauthenticated user CANNOT read user profile', async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(unauthedDb.doc('users/alice').get());
  });

  test('Alice CAN read and write her own user profile', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertSucceeds(aliceDb.doc('users/alice').set({ uid: 'alice', displayName: 'Alice' }));
    await assertSucceeds(aliceDb.doc('users/alice').get());
  });

  test('Bob CANNOT read or write Alice user profile', async () => {
    const bobDb = testEnv.authenticatedContext('bob').firestore();
    await assertFails(bobDb.doc('users/alice').get());
    await assertFails(bobDb.doc('users/alice').set({ displayName: 'Hacked' }));
  });

  test('Alice CAN manage her favorites subcollection', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertSucceeds(aliceDb.doc('users/alice/favorites/song1').set({ songId: 'song1', title: 'Song 1' }));
    await assertSucceeds(aliceDb.doc('users/alice/favorites/song1').get());
  });

  test('Bob CANNOT access Alice favorites subcollection', async () => {
    const bobDb = testEnv.authenticatedContext('bob').firestore();
    await assertFails(bobDb.doc('users/alice/favorites/song1').get());
  });

  test('Alice CANNOT access settings/credentials subcollection (Cloud Credential Sync Disabled)', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    // Cloud sync of raw session cookies is disabled; settings subcollection is no longer exposed
    await assertFails(aliceDb.doc('users/alice/settings/credentials').get());
    await assertFails(aliceDb.doc('users/alice/settings/credentials').set({ ytCookies: 'secret' }));
  });

  test('User CANNOT access unknown user-scoped subcollections (No Catch-All)', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertFails(aliceDb.doc('users/alice/arbitrary_subcol/doc1').get());
    await assertFails(aliceDb.doc('users/alice/arbitrary_subcol/doc1').set({ foo: 'bar' }));
  });

  // =========================================================================
  // 2. Root Favorites & Ownership Transfer Attack
  // =========================================================================
  test('Alice CAN manage her root favorites', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertSucceeds(aliceDb.doc('favorites/fav1').set({ userId: 'alice', title: 'My Song' }));
    await assertSucceeds(aliceDb.doc('favorites/fav1').get());
  });

  test('Creating root favorite with conflicting ownership fields (userId=alice, uid=bob) is DENIED', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertFails(aliceDb.doc('favorites/fav_conflict').set({
      userId: 'alice',
      uid: 'bob',
      title: 'Conflicting Ownership'
    }));
  });

  test('Creating root favorite with both matching ownership fields is ALLOWED', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertSucceeds(aliceDb.doc('favorites/fav_both').set({
      userId: 'alice',
      uid: 'alice',
      title: 'Valid Both Matching'
    }));
  });

  test('Creating root favorite without any ownership field is DENIED', async () => {
    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertFails(aliceDb.doc('favorites/fav_none').set({
      title: 'No Ownership'
    }));
  });

  test('User CANNOT transfer root favorite ownership to another user on update', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('favorites/fav1').set({ userId: 'alice', title: 'My Song' });
    });

    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertFails(aliceDb.doc('favorites/fav1').update({ userId: 'bob' }));
  });

  test('User CANNOT remove existing ownership field (userId) on update', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('favorites/fav1').set({ userId: 'alice', title: 'My Song' });
    });

    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    // Attempting to delete userId field
    await assertFails(aliceDb.doc('favorites/fav1').update({ userId: deleteField() }));
  });

  test('User CAN update non-ownership fields while preserving ownership field', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('favorites/fav1').set({ userId: 'alice', title: 'My Song' });
    });

    const aliceDb = testEnv.authenticatedContext('alice').firestore();
    await assertSucceeds(aliceDb.doc('favorites/fav1').update({ title: 'Updated Title' }));
  });

  // =========================================================================
  // 3. Listen Together Creation & Room Read Privacy
  // =========================================================================
  test('Host CAN create active room with self as host', async () => {
    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').set({
      roomCode: 'SRG-1001',
      hostId: 'host1',
      active: true,
      listenerCount: 1,
      participants: { host1: { id: 'host1', role: 'host' } }
    }));
  });

  test('Room creation with invalid schema (mismatched roomCode, listenerCount != 1, or spoofed hostId) is DENIED', async () => {
    const hostDb = testEnv.authenticatedContext('host1').firestore();
    // Mismatched roomCode
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').set({
      roomCode: 'WRONG_CODE',
      hostId: 'host1',
      active: true,
      listenerCount: 1,
      participants: { host1: { id: 'host1', role: 'host' } }
    }));
    // Spoofed hostId
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').set({
      roomCode: 'SRG-1001',
      hostId: 'attacker',
      active: true,
      listenerCount: 1,
      participants: { host1: { id: 'host1', role: 'host' } }
    }));
    // Arbitrary listenerCount != 1
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').set({
      roomCode: 'SRG-1001',
      hostId: 'host1',
      active: true,
      listenerCount: 50,
      participants: { host1: { id: 'host1', role: 'host' } }
    }));
  });

  test('Authenticated user CAN read active room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertSucceeds(guestDb.doc('listen_together_rooms/SRG-1001').get());
  });

  test('Stranger CANNOT read inactive closed room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: false,
        listenerCount: 0,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const strangerDb = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(strangerDb.doc('listen_together_rooms/SRG-1001').get());
  });

  test('Host CAN read their inactive room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: false,
        listenerCount: 0,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').get());
  });

  // =========================================================================
  // 4. Host Operations & Host Transfer Attacks
  // =========================================================================
  test('Host CAN update playback track and controls', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        songId: 'song1',
        isPlaying: true,
        positionMs: 1000,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').update({
      songId: 'song2',
      positionMs: 5000,
      isPlaying: false
    }));
  });

  test('Host CANNOT transfer hostId to another user', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      hostId: 'otherUser'
    }));
  });

  test('Host CAN delete their room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').delete());
  });

  test('Host CANNOT update unallowlisted field (allowlist enforcement)', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      unallowlistedField: 'malicious_injection'
    }));
  });

  test('Host CANNOT arbitrarily manipulate listenerCount while active', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 50
    }));
  });

  test('Host CANNOT inject unauthorized participant entries', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.fakeGuest': { id: 'fakeGuest', role: 'guest', lastSeenMs: 1000 }
    }));
  });

  test('Host CANNOT remove own host participant entry while room is active', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.host1': deleteField()
    }));
  });

  test('Host CAN prune stale participant entry and CAN deactivate room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          staleGuest: { id: 'staleGuest', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    // 1. Host prunes stale participant (updating listenerCount consistent with participant removal)
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 1,
      'participants.staleGuest': deleteField()
    }));

    // 2. Host closes room
    await assertSucceeds(hostDb.doc('listen_together_rooms/SRG-1001').update({
      active: false,
      listenerCount: 0
    }));
  });

  // =========================================================================
  // 5. Legitimate Guest Operations
  // =========================================================================
  test('Guest CAN join active room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertSucceeds(guestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 2,
      'participants.guest1': { id: 'guest1', role: 'guest', lastSeenMs: 2000 }
    }));
  });

  test('Active guest CAN send heartbeat updating own lastSeenMs', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertSucceeds(guestDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.guest1.lastSeenMs': 25000
    }));
  });

  test('Active guest CAN send valid reaction emoji', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertSucceeds(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'rx_1', emoji: '🔥', timestampMs: Date.now() }]
    }));
  });

  test('Guest CANNOT replace or delete existing reactions', async () => {
    const now = Date.now();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [{ id: 'rx_0', emoji: '❤️', timestampMs: now - 1000 }],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Replaces existing reaction rx_0 with rx_new instead of appending
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'rx_new', emoji: '🔥', timestampMs: now }]
    }));
  });

  test('Guest CANNOT reorder or swap existing reactions while appending', async () => {
    const now = Date.now();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [
          { id: 'rx_0', emoji: '❤️', timestampMs: now - 2000 },
          { id: 'rx_1', emoji: '👏', timestampMs: now - 1000 }
        ],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Swaps rx_0 and rx_1 order while appending rx_2
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [
        { id: 'rx_1', emoji: '👏', timestampMs: now - 1000 },
        { id: 'rx_0', emoji: '❤️', timestampMs: now - 2000 },
        { id: 'rx_2', emoji: '🔥', timestampMs: now }
      ]
    }));
  });

  test('Guest CANNOT append multiple reactions at once', async () => {
    const now = Date.now();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Appends two reactions in a single write
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [
        { id: 'rx_1', emoji: '🔥', timestampMs: now },
        { id: 'rx_2', emoji: '👏', timestampMs: now }
      ]
    }));
  });

  test('Guest reaction with future or stale timestamp is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Far-future timestamp (+10 minutes)
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'rx_future', emoji: '🔥', timestampMs: Date.now() + 600000 }]
    }));
  });

  test('Active guest CAN leave room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertSucceeds(guestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 1,
      'participants.guest1': deleteField()
    }));
  });

  // =========================================================================
  // 6. Guest Membership, Isolation & Tampering Attacks
  // =========================================================================
  test('Non-participant CANNOT send heartbeat without joining', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const strangerDb = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(strangerDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.stranger': { id: 'stranger', role: 'guest', lastSeenMs: 5000 }
    }));
  });

  test('Non-participant CANNOT inject reactions without joining', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: { host1: { id: 'host1', role: 'host' } }
      });
    });

    const strangerDb = testEnv.authenticatedContext('stranger').firestore();
    await assertFails(strangerDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'troll', emoji: '🔥', timestampMs: 3000 }]
    }));
  });

  test('Guest join modifying an existing participant is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          existingGuest: { id: 'existingGuest', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const newGuestDb = testEnv.authenticatedContext('newGuest').firestore();
    // newGuest tries to join while simultaneously tampering with existingGuest
    await assertFails(newGuestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 3,
      'participants.newGuest': { id: 'newGuest', role: 'guest', lastSeenMs: 2000 },
      'participants.existingGuest.lastSeenMs': 999999
    }));
  });

  test('Guest heartbeat modifying another guest is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 3,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 },
          victim: { id: 'victim', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // guest1 tries to modify victim's lastSeenMs
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.victim.lastSeenMs': 999999
    }));
  });

  test('Guest heartbeat attempting to inject unauthorized fields into participant entry is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Attempt role elevation or adding unauthorized field during heartbeat
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.guest1': { id: 'guest1', role: 'host', lastSeenMs: 2000 }
    }));
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.guest1.isAdmin': true
    }));
  });

  test('Guest leave CANNOT arbitrarily reset listenerCount to 0 (must decrement by exactly 1)', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 3,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 },
          guest2: { id: 'guest2', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // guest1 tries to reset listenerCount to 0 when leaving room with 3 listeners
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 0,
      'participants.guest1': deleteField()
    }));
  });

  test('Guest leave deleting another participant is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 3,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 },
          victim: { id: 'victim', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // guest1 tries to delete victim instead of self
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 2,
      'participants.victim': deleteField()
    }));
  });

  test('Guest reaction with forged identity or arbitrary fields is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Inject extra fields like spoofed sender or role
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'rx_1', emoji: '🔥', timestampMs: 3000, spoofedUser: 'admin', role: 'host' }]
    }));
  });

  test('Guest reaction with invalid payload (unsupported emoji) is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Unsupported emoji or malicious text injection
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [{ id: 'rx_1', emoji: '<script>alert(1)</script>', timestampMs: 3000 }]
    }));
  });

  test('Guest CANNOT remove or demote host in participants', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      'participants.host1': deleteField()
    }));
  });

  test('Guest CANNOT arbitrarily modify listenerCount', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 999
    }));
  });

  test('Guest CANNOT hijack playback songId or isPlaying', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        songId: 'originalSong',
        isPlaying: true,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      songId: 'hackedSong'
    }));
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      isPlaying: false
    }));
  });

  test('Guest CANNOT terminate room or delete it', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      active: false
    }));
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').delete());
  });

  test('Guest CANNOT modify an intermediate historical reaction in a longer reaction list', async () => {
    const now = Date.now();
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        recentReactions: [
          { id: 'rx_0', emoji: '❤️', timestampMs: now - 3000 },
          { id: 'rx_1', emoji: '🎵', timestampMs: now - 2000 },
          { id: 'rx_2', emoji: '👏', timestampMs: now - 1000 }
        ],
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const guestDb = testEnv.authenticatedContext('guest1').firestore();
    // Tamper with middle reaction (rx_1 emoji changed to 🔥) while appending rx_3
    await assertFails(guestDb.doc('listen_together_rooms/SRG-1001').update({
      recentReactions: [
        { id: 'rx_0', emoji: '❤️', timestampMs: now - 3000 },
        { id: 'rx_1', emoji: '🔥', timestampMs: now - 2000 },
        { id: 'rx_2', emoji: '👏', timestampMs: now - 1000 },
        { id: 'rx_3', emoji: '✨', timestampMs: now }
      ]
    }));
  });

  test('Participant with conflicting role: host but not matching hostId CANNOT perform host updates or delete room', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 2,
        songId: 'songA',
        participants: {
          host1: { id: 'host1', role: 'host' },
          rogue: { id: 'rogue', role: 'host', lastSeenMs: 1000 }
        }
      });
    });

    const rogueDb = testEnv.authenticatedContext('rogue').firestore();
    // Rogue participant has role: host in participants but hostId is host1 -> MUST FAIL
    await assertFails(rogueDb.doc('listen_together_rooms/SRG-1001').update({
      songId: 'hackedSong'
    }));
    await assertFails(rogueDb.doc('listen_together_rooms/SRG-1001').delete());
  });

  test('Host CANNOT demote self to guest role or change id in participants', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 1,
        participants: {
          host1: { id: 'host1', role: 'host' }
        }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    // Host attempts to demote own role in participants to guest
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      participants: {
        host1: { id: 'host1', role: 'guest' }
      }
    }));
    // Host attempts to change own id in participants
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      participants: {
        host1: { id: 'impostor', role: 'host' }
      }
    }));
  });

  test('Host pruning a participant with mismatched listenerCount is DENIED', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await context.firestore().doc('listen_together_rooms/SRG-1001').set({
        roomCode: 'SRG-1001',
        hostId: 'host1',
        active: true,
        listenerCount: 3,
        participants: {
          host1: { id: 'host1', role: 'host' },
          guest1: { id: 'guest1', role: 'guest', lastSeenMs: 1000 },
          guest2: { id: 'guest2', role: 'guest', lastSeenMs: 1000 }
        }
      });
    });

    const hostDb = testEnv.authenticatedContext('host1').firestore();
    // Removes guest1 (1 participant removed), but listenerCount is kept at 3 (or set to 1) instead of 2
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 3, // Inconsistent: 1 participant removed so count must be 2
      participants: {
        host1: { id: 'host1', role: 'host' },
        guest2: { id: 'guest2', role: 'guest', lastSeenMs: 1000 }
      }
    }));
    await assertFails(hostDb.doc('listen_together_rooms/SRG-1001').update({
      listenerCount: 1, // Inconsistent: 1 participant removed so count must be 2
      participants: {
        host1: { id: 'host1', role: 'host' },
        guest2: { id: 'guest2', role: 'guest', lastSeenMs: 1000 }
      }
    }));
  });

  // =========================================================================
  // 7. Default Deny
  // =========================================================================
  test('Default deny blocks arbitrary root collections', async () => {
    const authedDb = testEnv.authenticatedContext('alice').firestore();
    await assertFails(authedDb.doc('arbitrary_collection/doc1').get());
    await assertFails(authedDb.doc('arbitrary_collection/doc1').set({ foo: 'bar' }));
  });
});
