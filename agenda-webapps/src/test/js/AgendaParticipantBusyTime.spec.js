import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaEventFormDates from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDates.vue';
import AgendaEventFormBusyCoverage from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormBusyCoverage.vue';

/*
 * EXO-89850: the organiser sees the participants' busy time while dragging out
 * a date poll's candidate slots.
 *
 * The failure this file exists to prevent is not a rendering one. A
 * participant who does not share their busy time is simply NOT DRAWN, and an
 * empty grid reads as "free" — so the organiser picks a slot over a meeting
 * that is really there. The same is true of a participant whose read broke.
 *
 * Every pin is therefore written as a CONTRAST between participants whose
 * drawn content is identical and empty:
 *
 *   - `disclosed` with `busy: []`  -> a calendar was read and holds nothing.
 *                                     They ARE free, and nothing is said.
 *   - `not_disclosed`              -> no calendar was read. They are NAMED.
 *   - `failed`                     -> no calendar was read. They are NAMED,
 *                                     differently.
 *
 * A mutant that collapses any two of the three draws exactly the same grid, so
 * an assertion on the blocks alone would pass; what fails is the assertion on
 * which set the person landed in, and on the sentence the screen shows.
 */

/*
 * THE SHAPE THE FORM ACTUALLY BUILDS. social's convertSuggesterItemToIdentity
 * returns `{providerId, remoteId, profile}` and NO id; the attendees drawer
 * pushes exactly that. A participant only gains an identity id once the event
 * has been saved and the server has resolved them.
 *
 * The first cut of this feature filtered participants on `identity.id`, so on
 * a new event the list was always empty and nothing rendered at all — and
 * every pin passed, because every fixture carried an id the form never puts
 * there. Fixtures are now built the way the form builds them.
 *
 * @param {String} remoteId the username the suggester carries
 * @param {String} fullname how the person is named
 * @returns {Object} an attendee as the drawer pushes it
 */
function invited(remoteId, fullname) {
  return {
    identity: {
      providerId: 'organization',
      remoteId,
      profile: {avatar: `/avatar/${remoteId}`, fullname},
    },
    response: 'NEEDS_ACTION',
  };
}

/**
 * The same person once the server has resolved them.
 *
 * @param {String} id the identity id
 * @param {String} remoteId the username
 * @param {String} fullname how the person is named
 * @returns {Object} a resolved identity
 */
function resolvedIdentity(id, remoteId, fullname) {
  return {id, providerId: 'organization', remoteId, profile: {id, username: remoteId, fullname}};
}

const SHARER = invited('sara', 'Sara Green');

const WITHHOLDER = invited('tom', 'Tom Ray');

const BROKEN = invited('ana', 'Ana Diaz');

const NAMELESS = invited('ghost', 'Pat Vale');

/*
 * The organiser, as AgendaEventFormAttendees builds them: this one DOES carry
 * an id, from eXo.env.portal.userIdentityId.
 */
const ORGANISER = {identity: {id: '5', providerId: 'organization', remoteId: 'jsmith', profile: {id: '5', fullname: 'J Smith'}}};

const A_SPACE = {identity: {providerId: 'space', remoteId: 'marketing', profile: {fullname: 'Marketing'}}};

const IDENTITIES = {
  'organization:sara': resolvedIdentity('400', 'sara', 'Sara Green'),
  'organization:tom': resolvedIdentity('700', 'tom', 'Tom Ray'),
  'organization:ana': resolvedIdentity('800', 'ana', 'Ana Diaz'),
};

/**
 * A busy record as the endpoint answers it.
 *
 * @param {Object} attendee the participant it is about
 * @param {String} disclosure disclosed, not_disclosed or failed
 * @param {Array} busy the ranges, only ever present when disclosed
 * @returns {Object} the record
 */
function record(attendee, disclosure, busy) {
  const answer = {identityId: IDENTITIES[`organization:${attendee.identity.remoteId}`].id, disclosure};
  if (busy) {
    answer.busy = busy;
  }
  return answer;
}

/**
 * One busy range.
 *
 * @param {String} start ISO start
 * @param {String} end ISO end
 * @returns {Object} the range
 */
function block(start, end) {
  return {start, end};
}

/*
 * $agendaUtils is the SHIPPED module, not a stub: splitting the answer and
 * building the grid blocks are both under test.
 */
const baseVm = {
  participantBusyEvents: [],
  checkedParticipantKeys: [],
  notDisclosedParticipantKeys: [],
  failedParticipantKeys: [],
  spaceEvents: [],
  remoteEvents: [],
  busyTimeRequestId: 0,
  period: {start: new Date('2026-07-20T00:00:00Z'), end: new Date('2026-07-27T00:00:00Z')},
  $agendaUtils: agendaUtils,
  $t: key => key,
  refreshEventsToDisplay: () => {},
};

/**
 * An identity service that resolves the fixtures above and refuses anybody
 * else, counting what it was asked so a test can pin the memoisation.
 *
 * @param {Array} calls a log the resolver appends every lookup to
 * @returns {Object} a stand-in for $identityService
 */
function identityService(calls) {
  return {
    getIdentityByProviderIdAndRemoteId(providerId, remoteId) {
      calls.push(`${providerId}:${remoteId}`);
      const identity = IDENTITIES[`${providerId}:${remoteId}`];
      return identity ? Promise.resolve(identity) : Promise.reject(new Error('no such identity'));
    },
  };
}

/**
 * Drains the microtask queue far enough for the resolution-then-fetch chain
 * to have run to completion.
 *
 * @returns {Promise} resolves once the chain has settled
 */
function settle() {
  let chain = Promise.resolve();
  for (let step = 0; step < 8; step++) {
    chain = chain.then(() => Promise.resolve());
  }
  return chain;
}

/**
 * Runs the component's shipped busy-time retrieval against what the endpoint
 * answers, and returns the state it left behind.
 *
 * <p>
 * <strong>A factory, never a promise.</strong> `Promise.reject(...)` passed as
 * an argument is created when the argument is evaluated, and the component
 * only attaches its handler after resolving the participants' identities — so
 * the rejection sits unhandled across that step and Node's default policy
 * kills the process, failing the Maven build with every suite green. The
 * factory is invoked by the component at the moment it would really call the
 * service, which is when the handler goes on.
 *
 * @param {Array} participants the event's participants
 * @param {Function} answerFactory called by the component; returns what the
 *          availability service resolves or rejects with
 * @param {Array} calls a log the identity resolver appends to
 * @param {Object} existingVm a vm to reuse, for the memoisation pins
 * @returns {Promise} resolves with the resulting vm
 */
function retrieve(participants, answerFactory, calls, existingVm) {
  const vm = existingVm || Object.assign({}, baseVm, {
    resolvedParticipants: {},
    identityCalls: calls || [],
    $identityService: identityService(calls || []),
    $availabilityService: {
      getBusyTime: jest.fn(ids => {
        vm.askedIdentityIds = ids;
        return answerFactory();
      }),
    },
  });
  vm.participants = participants;
  // The methods under test are the SHIPPED ones, called on a plain vm.
  ['forgetParticipantsBusyTime', 'applyParticipantsBusyTime', 'resolveParticipants'].forEach(name => {
    vm[name] = (...args) => AgendaEventFormDates.methods[name].apply(vm, args);
  });
  AgendaEventFormDates.methods.retrieveParticipantsBusyTime.call(vm);
  return settle().then(() => vm);
}

/**
 * Evaluates the coverage strip's computeds over the state a retrieval left.
 *
 * @param {Array} participants the event's participants
 * @param {Object} vm the state left by a retrieval
 * @returns {Object} the strip's own state
 */
function coverage(participants, vm) {
  const stripVm = {
    participants,
    checkedKeys: vm.checkedParticipantKeys,
    notDisclosedKeys: vm.notDisclosedParticipantKeys,
    failedKeys: vm.failedParticipantKeys,
    $agendaUtils: agendaUtils,
    $t: (key, params) => `${key}|${params && Object.keys(params).map(index => params[index]).join('|')}`,
    displayNameOf: AgendaEventFormBusyCoverage.methods.displayNameOf,
  };
  stripVm.keyOf = participant => AgendaEventFormBusyCoverage.methods.keyOf.call(stripVm, participant);
  stripVm.namesOf = ids => AgendaEventFormBusyCoverage.methods.namesOf.call(stripVm, ids);
  const evaluate = name => AgendaEventFormBusyCoverage.computed[name].call(stripVm);
  stripVm.notDisclosedNames = evaluate('notDisclosedNames');
  stripVm.failedNames = evaluate('failedNames');
  stripVm.checkedCount = evaluate('checkedCount');
  stripVm.participantCount = evaluate('participantCount');
  return {
    participantCount: stripVm.participantCount,
    checkedCount: stripVm.checkedCount,
    notDisclosedNames: stripVm.notDisclosedNames,
    failedNames: stripVm.failedNames,
    coverageSentence: evaluate('coverageSentence'),
    notDisclosedSentence: evaluate('notDisclosedSentence'),
    failedSentence: evaluate('failedSentence'),
  };
}

describe('AgendaUtils keeps the three busy-time outcomes apart', () => {
  it('counts a read-and-empty participant as checked', () => {
    const split = agendaUtils.splitBusyTimeResults([record(SHARER, 'disclosed', [])]);

    expect(split.checkedIds).toEqual(['400']);
    expect(split.notDisclosedIds).toHaveLength(0);
    expect(split.failedIds).toHaveLength(0);
    expect(split.busyByIdentityId['400']).toEqual([]);
  });

  it('does not count a participant who shares nothing as checked', () => {
    // Same drawn content as the case above — none — and the opposite meaning.
    const split = agendaUtils.splitBusyTimeResults([record(WITHHOLDER, 'not_disclosed')]);

    expect(split.checkedIds).toHaveLength(0);
    expect(split.notDisclosedIds).toEqual(['700']);
    expect(split.busyByIdentityId['700']).toBeUndefined();
  });

  it('does not count a participant whose read broke as checked', () => {
    const split = agendaUtils.splitBusyTimeResults([record(BROKEN, 'failed')]);

    expect(split.checkedIds).toHaveLength(0);
    expect(split.failedIds).toEqual(['800']);
    expect(split.notDisclosedIds).toHaveLength(0);
  });

  it('treats a status it does not recognise as unchecked, never as an answer', () => {
    const split = agendaUtils.splitBusyTimeResults([record(SHARER, 'something-new', [])]);

    expect(split.checkedIds).toHaveLength(0);
    expect(split.notDisclosedIds).toEqual(['400']);
  });

  it('carries only the two instants of a block onto the grid', () => {
    // The endpoint never sends a title, a location or a calendar; if one ever
    // arrived, nothing of it may reach a grid block.
    const leaky = Object.assign(block('2026-07-20T09:00:00+02:00', '2026-07-20T10:00:00+02:00'), {
      summary: 'Board review',
      location: 'Room 4',
      calendarName: 'Personal',
    });

    const events = agendaUtils.toParticipantBusyEvents([leaky], SHARER, 'Busy');

    expect(events).toHaveLength(1);
    expect(events[0].summary).toBe('Busy');
    expect(events[0].location).toBeUndefined();
    expect(events[0].calendarName).toBeUndefined();
    // Read as a wall-clock time, like every other event on this grid: the
    // server already rendered the range in the reader's own zone.
    expect(events[0].startDate).toEqual(new Date('2026-07-20T09:00:00'));
    expect(events[0].endDate).toEqual(new Date('2026-07-20T10:00:00'));
  });
});

describe('The date-poll grid draws the participants busy time', () => {
  it('draws the blocks of a participant who shares them', () => {
    return retrieve([SHARER], () => Promise.resolve([
      record(SHARER, 'disclosed', [block('2026-07-20T09:00:00+02:00', '2026-07-20T10:00:00+02:00')]),
    ])).then(vm => {
      expect(vm.participantBusyEvents).toHaveLength(1);
      expect(vm.participantBusyEvents[0].identityId).toBe('400');
      expect(vm.checkedParticipantKeys).toEqual(['organization:sara']);
      // Resolved before asking: the endpoint speaks identity ids, and the
      // form had none to give it.
      expect(vm.askedIdentityIds).toEqual(['400']);
    });
  });

  it('draws no block for a participant who shares nothing, and names them', () => {
    return retrieve([SHARER, WITHHOLDER], () => Promise.resolve([
      record(SHARER, 'disclosed', []),
      record(WITHHOLDER, 'not_disclosed'),
    ])).then(vm => {
      // Identical grids for both — nothing drawn — and only the coverage
      // state says that one of them was never asked.
      expect(vm.participantBusyEvents).toHaveLength(0);
      expect(vm.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(vm.notDisclosedParticipantKeys).toEqual(['organization:tom']);

      const strip = coverage([SHARER, WITHHOLDER], vm);
      expect(strip.checkedCount).toBe(1);
      expect(strip.participantCount).toBe(2);
      expect(strip.notDisclosedNames).toEqual(['Tom Ray']);
      expect(strip.failedNames).toHaveLength(0);
    });
  });

  it('names a participant whose own read broke apart from one who shares nothing', () => {
    return retrieve([WITHHOLDER, BROKEN], () => Promise.resolve([
      record(WITHHOLDER, 'not_disclosed'),
      record(BROKEN, 'failed'),
    ])).then(vm => {
      const strip = coverage([WITHHOLDER, BROKEN], vm);
      expect(strip.checkedCount).toBe(0);
      expect(strip.notDisclosedNames).toEqual(['Tom Ray']);
      expect(strip.failedNames).toEqual(['Ana Diaz']);
      // Two sentences, two message keys: a choice and a breakage never read
      // as the same thing to the organiser.
      expect(strip.notDisclosedSentence).toContain('agenda.eventForm.busyTimeNotShared');
      expect(strip.failedSentence).toContain('agenda.eventForm.busyTimeNotChecked');
    });
  });

  it('says nothing about a participant who was read and has nothing on', () => {
    return retrieve([SHARER], () => Promise.resolve([record(SHARER, 'disclosed', [])])).then(vm => {
      const strip = coverage([SHARER], vm);
      expect(strip.notDisclosedNames).toHaveLength(0);
      expect(strip.failedNames).toHaveLength(0);
      // And it still states its coverage, so that the empty grid can be read
      // as "they are free" rather than as "nothing ran".
      expect(strip.coverageSentence).toBe('agenda.eventForm.busyTimeCoverage|1|1');
    });
  });

  it('makes every participant unchecked when the whole read fails', () => {
    return retrieve([SHARER, WITHHOLDER], () => Promise.reject(new Error('gateway down'))).then(vm => {
      expect(vm.participantBusyEvents).toHaveLength(0);
      expect(vm.checkedParticipantKeys).toHaveLength(0);
      expect(vm.failedParticipantKeys).toEqual(['organization:sara', 'organization:tom']);

      const strip = coverage([SHARER, WITHHOLDER], vm);
      expect(strip.checkedCount).toBe(0);
      expect(strip.failedNames).toEqual(['Sara Green', 'Tom Ray']);
      expect(strip.coverageSentence).toBe('agenda.eventForm.busyTimeCoverage|0|2');
    });
  });

  it('reports everybody unread when the read throws instead of rejecting', () => {
    // A service that throws SYNCHRONOUSLY bypasses the catch attached to its
    // return value and lands in the outer chain. A chain terminated by
    // `finally` re-throws that, leaving an unhandled rejection — a console
    // error in a browser, a killed process under the Node the build uses —
    // and, worse here, a grid emptied with nobody named on it.
    return retrieve([SHARER, WITHHOLDER], () => {
      throw new Error('service exploded');
    }).then(vm => {
      expect(vm.participantBusyEvents).toHaveLength(0);
      expect(vm.checkedParticipantKeys).toHaveLength(0);
      expect(vm.failedParticipantKeys).toEqual(['organization:sara', 'organization:tom']);
    });
  });

  it('drops a stale answer about somebody who has left the event', () => {
    return retrieve([SHARER, WITHHOLDER], () => Promise.resolve([
      record(SHARER, 'disclosed', []),
      record(WITHHOLDER, 'disclosed', []),
    ])).then(vm => {
      // The organiser removed Tom Ray after the read went out; the coverage
      // must not claim to have checked somebody who is no longer invited.
      const strip = coverage([SHARER], vm);
      expect(strip.checkedCount).toBe(1);
      expect(strip.participantCount).toBe(1);
    });
  });
});

describe('The date-poll grid chooses whose busy time it asks for', () => {
  /**
   * Evaluates the form's participants computed over an attendee list.
   *
   * @param {Array} attendees the event's attendees
   * @returns {Array} the participants the grid will ask about
   */
  function participantsOf(attendees) {
    return AgendaEventFormDates.computed.participants.call({
      event: {attendees},
      $agendaUtils: agendaUtils,
    });
  }

  /**
   * The keys of a participant list.
   *
   * @param {Array} participants the list
   * @returns {Array} their participant keys
   */
  function keysOf(participants) {
    return participants.map(participant => agendaUtils.participantKey(participant));
  }

  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
    global.eXo.env.portal.userName = 'jsmith';
  });

  it('keeps a participant the suggester added, who carries no identity id yet', () => {
    // THE PIN THAT WAS MISSING. Every fixture used to carry an id the form
    // never puts there, so a filter on `identity.id` looked correct and
    // dropped every participant of a new event on the real screen.
    expect(SHARER.identity.id).toBeUndefined();

    expect(keysOf(participantsOf([ORGANISER, SHARER]))).toEqual(['organization:sara']);
  });

  it('leaves the organiser out even when the comparison has no id to use', () => {
    // On a new event the organiser is the ONLY attendee carrying an id, so
    // matching on the id alone happens to work here — and would stop working
    // the moment they too arrived through the suggester. The username is the
    // half that always holds.
    const invitedOrganiser = invited('jsmith', 'J Smith');

    expect(keysOf(participantsOf([invitedOrganiser, SHARER]))).toEqual(['organization:sara']);
  });

  it('leaves a space out rather than expanding it into its members', () => {
    expect(keysOf(participantsOf([A_SPACE, SHARER]))).toEqual(['organization:sara']);
  });

  it('drops an attendee carrying neither a provider pair nor an id', () => {
    expect(participantsOf([{identity: {}}, SHARER])).toHaveLength(1);
  });
});

describe('The date-poll grid resolves the participants it was handed', () => {
  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
    global.eXo.env.portal.userName = 'jsmith';
  });

  it('names a participant whose identity could not be resolved, rather than dropping them', () => {
    // Pat Vale is not in the directory the stub answers for. Nothing can be
    // read about them, so they are a breakage — not a choice, and above all
    // not somebody who is free.
    return retrieve([SHARER, NAMELESS], () => Promise.resolve([record(SHARER, 'disclosed', [])])).then(vm => {
      expect(vm.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(vm.failedParticipantKeys).toEqual(['organization:ghost']);

      const strip = coverage([SHARER, NAMELESS], vm);
      expect(strip.checkedCount).toBe(1);
      expect(strip.participantCount).toBe(2);
      expect(strip.failedNames).toEqual(['Pat Vale']);
      expect(strip.coverageSentence).toBe('agenda.eventForm.busyTimeCoverage|1|2');
    });
  });

  it('never asks the endpoint when nobody could be resolved', () => {
    let asked = false;
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService([]),
      $availabilityService: {getBusyTime: () => {
        asked = true;
        return Promise.resolve([]);
      }},
    });

    return retrieve([NAMELESS], () => Promise.resolve([]), [], vm).then(state => {
      expect(asked).toBe(false);
      expect(state.failedParticipantKeys).toEqual(['organization:ghost']);
      expect(state.checkedParticipantKeys).toHaveLength(0);
    });
  });

  it('resolves each participant once, however often the grid is paged', () => {
    const calls = [];
    const answer = () => Promise.resolve([record(SHARER, 'disclosed', []), record(WITHHOLDER, 'disclosed', [])]);
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService(calls),
      $availabilityService: {getBusyTime: () => answer()},
    });

    return retrieve([SHARER, WITHHOLDER], () => Promise.resolve([]), calls, vm)
      .then(state => retrieve([SHARER, WITHHOLDER], () => Promise.resolve([]), calls, state))
      .then(state => retrieve([SHARER, WITHHOLDER], () => Promise.resolve([]), calls, state))
      .then(() => {
        // Three navigations, two people, two lookups. Without the memo this
        // is six, and a month of paging is dozens.
        expect(calls).toEqual(['organization:sara', 'organization:tom']);
      });
  });

  it('does not retry a lookup that already failed', () => {
    const calls = [];
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService(calls),
      $availabilityService: {getBusyTime: () => Promise.resolve([])},
    });

    return retrieve([NAMELESS], () => Promise.resolve([]), calls, vm)
      .then(state => retrieve([NAMELESS], () => Promise.resolve([]), calls, state))
      .then(state => {
        expect(calls).toEqual(['organization:ghost']);
        // And the answer stays the honest one across both passes.
        expect(state.failedParticipantKeys).toEqual(['organization:ghost']);
      });
  });

  it('treats a lookup that answers without an id as having resolved nobody', () => {
    // EXO-89843's lesson one layer down: a 200 carrying nothing is not an
    // answer. Recording it as a success would send `undefined` to the
    // endpoint and lose the person from the strip as well as from the grid.
    let asked = false;
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: {getIdentityByProviderIdAndRemoteId: () => Promise.resolve({})},
      $availabilityService: {getBusyTime: () => {
        asked = true;
        return Promise.resolve([]);
      }},
    });

    return retrieve([SHARER], () => Promise.resolve([]), [], vm).then(state => {
      expect(asked).toBe(false);
      expect(state.failedParticipantKeys).toEqual(['organization:sara']);
      expect(state.checkedParticipantKeys).toHaveLength(0);
    });
  });

  it('counts a participant the answer says nothing about as unread', () => {
    // Two people asked about, one answered for. Silence is not an answer, and
    // the silent one must not inherit the other one's "checked".
    return retrieve([SHARER, WITHHOLDER], () => Promise.resolve([record(SHARER, 'disclosed', [])])).then(vm => {
      expect(vm.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(vm.failedParticipantKeys).toEqual(['organization:tom']);

      const strip = coverage([SHARER, WITHHOLDER], vm);
      expect(strip.checkedCount).toBe(1);
      expect(strip.failedNames).toEqual(['Tom Ray']);
    });
  });

  it('does not let a stale read overwrite a newer one', () => {
    // The organiser pages the grid while a read is still in flight. The old
    // answer must not repaint the strip it no longer describes.
    let releaseSlow = null;
    const answers = [new Promise(resolve => {
      releaseSlow = resolve;
    })];
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService([]),
      $availabilityService: {getBusyTime: () => answers.shift()},
    });
    ['forgetParticipantsBusyTime', 'applyParticipantsBusyTime', 'resolveParticipants'].forEach(name => {
      vm[name] = (...args) => AgendaEventFormDates.methods[name].apply(vm, args);
    });

    vm.participants = [SHARER];
    AgendaEventFormDates.methods.retrieveParticipantsBusyTime.call(vm);

    return settle()
      .then(() => {
        answers.push(Promise.resolve([record(WITHHOLDER, 'not_disclosed')]));
        vm.participants = [WITHHOLDER];
        AgendaEventFormDates.methods.retrieveParticipantsBusyTime.call(vm);
        return settle();
      })
      .then(() => {
        releaseSlow([record(SHARER, 'disclosed', [])]);
        return settle();
      })
      .then(() => {
        expect(vm.notDisclosedParticipantKeys).toEqual(['organization:tom']);
        expect(vm.checkedParticipantKeys).toHaveLength(0);
        expect(vm.participantBusyEvents).toHaveLength(0);
      });
  });
});

/*
 * EXO-89867: inviting a guest by email blanked the busy time of everyone else.
 *
 * THE MECHANISM, MEASURED ON THE RUNNING PLATFORM, not inferred from the
 * symptom. A guest is pushed by `saveGuestEmail` with `identity.id` set to the
 * EMAIL ADDRESS, so `resolveParticipants` took its "already has an id"
 * short-circuit and recorded the guest as RESOLVED — it never looked anybody
 * up and never failed. The address then travelled into the one batched
 * request, whose parameter is `List<Long>`:
 *
 *   GET /agenda/rest/availability?identityIds=13              -> 200 + blocks
 *   GET /agenda/rest/availability?identityIds=13,a@acme.com   -> 400
 *      "Failed to convert value of type 'java.lang.String' to required type
 *       'java.util.List'; For input string: \"13,a@acme.com\""
 *
 * Spring binds the whole comma-joined list at once, so a single unaskable
 * member is a 400 on the REQUEST, not a member the server skips. The 400 then
 * met EXO-89850's own rule — a rejected read means nothing is known about
 * ANYBODY — and that rule, correct in itself, fanned one guest's presence out
 * over every real colleague on the list.
 *
 * The two halves are therefore pinned separately, because either one alone
 * leaves the defect reachable:
 *   - a guest is not a participant of this grid at all;
 *   - and a participant who cannot be reduced to an askable identity id is
 *     kept OUT of the batch and named, while everybody else is still asked.
 *
 * The availability stub below models the endpoint's real contract rather than
 * a friendly one: it REJECTS the whole call when any id is not numeric. A stub
 * that quietly ignored the bad id would pass against the very bug this file
 * exists to prevent.
 */

/*
 * A guest, exactly as AgendaEventFormAttendeesDrawer.saveGuestEmail builds
 * them — including `identity.id` holding the address, which is what made the
 * old truthiness check treat this as a resolved identity.
 */
const GUEST = {
  identity: {
    id: 'guest@acme.com',
    remoteId: 'guest@acme.com',
    identityId: 'guest@acme.com',
    providerId: 'GUEST_USER',
    profile: {
      fullName: 'guest@acme.com',
      avatarUrl: '/portal/rest/v1/social/users/default-image/avatar',
    },
  },
};

/*
 * A NON-guest carrying something that is not an identity id where an identity
 * id is expected. This is the general shape of the regression, and it is why
 * excluding guests is not on its own enough: any participant the platform
 * cannot reduce to a technical id would take the whole batch down the same
 * way.
 */
const MISKEYED = {
  identity: {
    id: 'not-an-identity-id',
    providerId: 'organization',
    remoteId: 'ghost',
    profile: {fullname: 'Pat Vale'},
  },
};

/**
 * An availability service that answers the way the resource really does: the
 * whole call fails when any member of the list is not an identity id.
 *
 * @param {Object} vm the vm to record the asked ids on
 * @param {Array} records what a well-formed call answers
 * @returns {Object} a stand-in for $availabilityService
 */
function strictAvailabilityService(vm, records) {
  return {
    getBusyTime(ids) {
      vm.askedIdentityIds = ids;
      if ((ids || []).some(id => !/^\d+$/.test(String(id)))) {
        // Spring's own failure: the parameter never binds, so the method that
        // would have answered about the others is never entered.
        return Promise.reject(new Error('Response code indicates a server error'));
      }
      return Promise.resolve(records);
    },
  };
}

/**
 * Builds a vm wired to the strict service above.
 *
 * @param {Array} records what a well-formed call answers
 * @returns {Object} the vm
 */
function strictVm(records) {
  const vm = Object.assign({}, baseVm, {
    resolvedParticipants: {},
    $identityService: identityService([]),
  });
  vm.$availabilityService = strictAvailabilityService(vm, records);
  return vm;
}

describe('AgendaUtils tells an identity id from anything else', () => {
  it('accepts a technical identity id', () => {
    expect(agendaUtils.isIdentityId('400')).toBe(true);
    expect(agendaUtils.isIdentityId(400)).toBe(true);
  });

  it('refuses the email address a guest carries in place of one', () => {
    // The value `saveGuestEmail` really puts in `identity.id`.
    expect(agendaUtils.isIdentityId('guest@acme.com')).toBe(false);
  });

  it('refuses what an absent or malformed id looks like', () => {
    expect(agendaUtils.isIdentityId(undefined)).toBe(false);
    expect(agendaUtils.isIdentityId(null)).toBe(false);
    expect(agendaUtils.isIdentityId('')).toBe(false);
    expect(agendaUtils.isIdentityId('12a')).toBe(false);
    expect(agendaUtils.isIdentityId(' 12')).toBe(false);
    // `String(['400'])` is '400': a regexp on its own would take this for an
    // id and put an array into the query string.
    expect(agendaUtils.isIdentityId(['400'])).toBe(false);
  });
});

describe('EXO-89867: a guest never blanks the people who do have a calendar', () => {
  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
    global.eXo.env.portal.userName = 'jsmith';
  });

  /**
   * Evaluates the form's participants computed over an attendee list.
   *
   * @param {Array} attendees the event's attendees
   * @returns {Array} the participants the grid will ask about
   */
  function participantsOf(attendees) {
    return AgendaEventFormDates.computed.participants.call({
      event: {attendees},
      $agendaUtils: agendaUtils,
    });
  }

  it('leaves a guest out of the participants, as it already leaves a space out', () => {
    // A guest passes every OTHER filter: they have a participant key, their
    // provider is not `space`, and they are not the current user. That is
    // precisely how they reached the request.
    expect(agendaUtils.participantKey(GUEST)).toBe('GUEST_USER:guest@acme.com');
    expect(GUEST.identity.providerId).not.toBe('space');

    const participants = participantsOf([ORGANISER, SHARER, GUEST]);

    expect(participants.map(participant => agendaUtils.participantKey(participant)))
      .toEqual(['organization:sara']);
  });

  it('asks only about the colleague, and draws their busy time', () => {
    // THE PIN FOR THE REPORTED SYMPTOM. Sara's calendar is readable and holds
    // a meeting; before the fix the guest's presence turned that into an
    // empty week for her too.
    const participants = participantsOf([ORGANISER, SHARER, GUEST]);
    const vm = strictVm([record(SHARER, 'disclosed', [block('2026-07-20T09:00:00+02:00', '2026-07-20T10:00:00+02:00')])]);

    return retrieve(participants, null, [], vm).then(state => {
      expect(state.askedIdentityIds).toEqual(['400']);
      expect(state.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(state.participantBusyEvents).toHaveLength(1);
      expect(state.failedParticipantKeys).toHaveLength(0);
    });
  });

  it('says nothing at all about the guest, and still shows the colleague as free', () => {
    // The decision this change makes, pinned so it cannot drift back: a guest
    // is OMITTED, never named as unreadable. Sara was read and has nothing
    // on — she is free, and the strip has to be able to say so, which it can
    // only do if the guest is not counted against its coverage.
    const participants = participantsOf([ORGANISER, SHARER, GUEST]);
    const vm = strictVm([record(SHARER, 'disclosed', [])]);

    return retrieve(participants, null, [], vm).then(state => {
      expect(state.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(state.notDisclosedParticipantKeys).toHaveLength(0);
      expect(state.failedParticipantKeys).toHaveLength(0);
      expect(state.participantBusyEvents).toHaveLength(0);

      const strip = coverage(participants, state);
      expect(strip.participantCount).toBe(1);
      expect(strip.checkedCount).toBe(1);
      expect(strip.failedNames).toHaveLength(0);
      expect(strip.notDisclosedNames).toHaveLength(0);
      // 1 of 1, not 1 of 2: the grid covers everybody it undertook to cover,
      // and the empty week reads as "Sara is free" rather than as a gap.
      expect(strip.coverageSentence).toBe('agenda.eventForm.busyTimeCoverage|1|1');
    });
  });

  it('never lets a guest reach the identity lookup either', () => {
    // Not a cost point: a lookup that ANSWERED would put the guest back in
    // the batch, since GUEST_USER is a real social identity provider.
    const calls = [];
    const participants = participantsOf([ORGANISER, GUEST]);
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService(calls),
    });
    vm.$availabilityService = strictAvailabilityService(vm, []);

    return retrieve(participants, null, calls, vm).then(state => {
      expect(participants).toHaveLength(0);
      expect(calls).toHaveLength(0);
      expect(state.askedIdentityIds).toBeUndefined();
    });
  });
});

describe('EXO-89867: one unaskable participant does not blank the rest', () => {
  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
    global.eXo.env.portal.userName = 'jsmith';
  });

  it('keeps a participant with no askable id out of the batch and names them', () => {
    // THE HALF THAT OUTLIVES THE GUEST FIX. Nothing here is a guest: this is
    // any participant the platform cannot reduce to an identity id. Before
    // the fix `identity.id` was believed because it was truthy, the address
    // went into the list, and Sara — whose calendar is perfectly readable —
    // lost her busy time to it.
    const vm = strictVm([record(SHARER, 'disclosed', [block('2026-07-20T09:00:00+02:00', '2026-07-20T10:00:00+02:00')])]);

    return retrieve([SHARER, MISKEYED], null, [], vm).then(state => {
      expect(state.askedIdentityIds).toEqual(['400']);
      // Sara is drawn.
      expect(state.checkedParticipantKeys).toEqual(['organization:sara']);
      expect(state.participantBusyEvents).toHaveLength(1);
      // And the one who could not be asked about is NAMED, not dropped and
      // not drawn as free: nothing was read about them.
      expect(state.failedParticipantKeys).toEqual(['organization:ghost']);

      const strip = coverage([SHARER, MISKEYED], state);
      expect(strip.checkedCount).toBe(1);
      expect(strip.participantCount).toBe(2);
      expect(strip.failedNames).toEqual(['Pat Vale']);
      expect(strip.failedSentence).toContain('agenda.eventForm.busyTimeNotChecked');
    });
  });

  it('refuses a lookup that answers with something that is not an identity id', () => {
    // The same rule one layer down. A 200 carrying `{id: 'a@b.com'}` is not a
    // resolution; believing it puts the value straight back into the batch.
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: {
        getIdentityByProviderIdAndRemoteId: () => Promise.resolve({id: 'a@b.com', providerId: 'organization', remoteId: 'sara'}),
      },
    });
    vm.$availabilityService = strictAvailabilityService(vm, []);

    return retrieve([SHARER], null, [], vm).then(state => {
      // Nobody left to ask about, so nothing is asked — and Sara is declared
      // unread rather than quietly left off a grid that reads as free.
      expect(state.askedIdentityIds).toBeUndefined();
      expect(state.failedParticipantKeys).toEqual(['organization:sara']);
      expect(state.checkedParticipantKeys).toHaveLength(0);
    });
  });

  it('still reports everybody unread when the read itself breaks', () => {
    // The guard narrows what reaches the endpoint; it does not soften what a
    // genuine failure means. Both participants are askable here, the call
    // fails, and neither is drawn as free.
    const vm = Object.assign({}, baseVm, {
      resolvedParticipants: {},
      $identityService: identityService([]),
      $availabilityService: {getBusyTime: () => Promise.reject(new Error('gateway down'))},
    });

    return retrieve([SHARER, WITHHOLDER], null, [], vm).then(state => {
      expect(state.failedParticipantKeys).toEqual(['organization:sara', 'organization:tom']);
      expect(state.checkedParticipantKeys).toHaveLength(0);
      expect(state.participantBusyEvents).toHaveLength(0);
    });
  });
});
