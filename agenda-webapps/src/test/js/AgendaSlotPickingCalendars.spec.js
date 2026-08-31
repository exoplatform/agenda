import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaEventFormDates from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDates.vue';

/*
 * EXO-89851: the slot-picking grid used to ask ONE calendar — the owner of the
 * event being created — so creating a poll in a space drew that space's week
 * and nothing else. The organiser's personal calendar, their other spaces and
 * their materialised remote calendars were all invisible at the moment of
 * choosing, and they picked a slot over their own meeting with the screen
 * saying nothing.
 *
 * The pins below are about two properties, in this order of importance:
 *
 *   1. BOTH sources are asked, and an event in both is drawn once.
 *   2. A source that FAILED is never rendered as a source that answered
 *      "nothing" — same contract as EXO-89842 and EXO-89843, and it matters
 *      more here because this grid produces a decision.
 *
 * Property 2 is written as a contrast throughout: a read that resolves with an
 * empty list and a read that rejects put the same (nothing) on the grid, and
 * only the warning tells them apart. A mutant that collapses them draws an
 * identical grid, so the assertion that fails is the one on the warning.
 */

const TARGET_CALENDAR_OWNER = {providerId: 'space', remoteId: 'marketing'};

const SPACE_CALENDAR = {id: '10', name: 'Marketing', color: '#319ab3'};

const PERSONAL_CALENDAR = {id: '11', system: true, title: 'John Smith', color: '#bc99e7'};

/**
 * An event as the events endpoint answers it.
 *
 * @param {String} id the event id
 * @param {Object} calendar the calendar it lives in
 * @returns {Object} the event
 */
function storeEvent(id, calendar) {
  return {
    id,
    summary: `event ${id}`,
    calendar,
    start: '2026-07-20T09:00:00+02:00',
    end: '2026-07-20T10:00:00+02:00',
  };
}

/**
 * Builds a vm carrying the shipped methods, with both reads stubbed.
 *
 * @param {Object} options `{targetEvents, ownEvents, targetFails, ownFails}`
 * @returns {Object} the vm
 */
function formVm(options) {
  const calls = [];
  const vm = {
    spaceEvents: [],
    failedStoreSourceKeys: [],
    failedConnectors: [],
    storeRequestId: 0,
    period: {start: new Date('2026-07-20T00:00:00Z'), end: new Date('2026-07-27T00:00:00Z')},
    event: {calendar: {owner: TARGET_CALENDAR_OWNER}},
    $agendaUtils: agendaUtils,
    $t: key => key,
    $nextTick: () => Promise.resolve(),
    refreshEventsToDisplay: () => {},
    eventServiceCalls: calls,
    $identityService: {
      getIdentityByProviderIdAndRemoteId: () => Promise.resolve({id: '900'}),
    },
    $eventService: {
      getEvents(query, ownerIds, attendeeIdentityId, start, end, limit, responseTypes) {
        calls.push({ownerIds, attendeeIdentityId, responseTypes});
        const asksTargetCalendar = ownerIds && ownerIds.length > 0;
        if (asksTargetCalendar) {
          return options.targetFails
            ? Promise.reject(new Error('calendar unreachable'))
            : Promise.resolve({events: (options.targetEvents || []).slice()});
        }
        return options.ownFails
          ? Promise.reject(new Error('own calendars unreachable'))
          : Promise.resolve({events: (options.ownEvents || []).slice()});
      },
    },
  };
  ['readTargetCalendarEvents', 'readOwnEvents', 'readStoreEvents', 'mergeStoreEvents', 'storeEventTitle']
    .forEach(name => {
      vm[name] = (...args) => AgendaEventFormDates.methods[name].apply(vm, args);
    });
  return vm;
}

/**
 * Runs the shipped store read and returns the state it left behind.
 *
 * @param {Object} options what each source answers
 * @returns {Promise} resolves with the vm
 */
function readStore(options) {
  const vm = formVm(options);
  AgendaEventFormDates.methods.retrieveEventsFromStore.call(vm);
  let chain = Promise.resolve();
  for (let step = 0; step < 8; step++) {
    chain = chain.then(() => Promise.resolve());
  }
  return chain.then(() => vm);
}

/**
 * Evaluates the shipped failed-source computeds over a vm.
 *
 * @param {Object} vm the state to evaluate them against
 * @returns {Object} `{names, hasFailedSource}`
 */
function failureState(vm) {
  const names = AgendaEventFormDates.computed.failedSourceNames.call(vm);
  return {
    names,
    hasFailedSource: AgendaEventFormDates.computed.hasFailedSource.call({failedSourceNames: names}),
  };
}

describe('The slot-picking grid asks every calendar the organiser has', () => {
  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
  });

  it('asks the target calendar and the organiser own commitments, not one of them', () => {
    return readStore({
      targetEvents: [storeEvent('1', SPACE_CALENDAR)],
      ownEvents: [storeEvent('2', PERSONAL_CALENDAR)],
    }).then(vm => {
      const target = vm.eventServiceCalls.find(call => call.ownerIds.length);
      const own = vm.eventServiceCalls.find(call => !call.ownerIds.length);

      expect(target.ownerIds).toEqual(['900']);
      // The second read is the one that was missing: everything the viewer is
      // an attendee of, wherever it lives.
      expect(own.attendeeIdentityId).toBe('5');
      expect(own.responseTypes).toEqual(['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE']);
    });
  });

  it('draws the personal events that used to be invisible', () => {
    return readStore({
      targetEvents: [storeEvent('1', SPACE_CALENDAR)],
      ownEvents: [storeEvent('2', PERSONAL_CALENDAR)],
    }).then(vm => {
      expect(vm.spaceEvents.map(event => event.id)).toEqual(['1', '2']);
    });
  });

  it('draws an event that is in both calendars once', () => {
    return readStore({
      targetEvents: [storeEvent('1', SPACE_CALENDAR)],
      ownEvents: [storeEvent('1', SPACE_CALENDAR), storeEvent('2', PERSONAL_CALENDAR)],
    }).then(vm => {
      expect(vm.spaceEvents.map(event => event.id)).toEqual(['1', '2']);
    });
  });

  it('keeps two events that both carry no id, since two absent ids are not a match', () => {
    const anonymous = () => Object.assign(storeEvent('x', SPACE_CALENDAR), {id: null});
    return readStore({targetEvents: [anonymous()], ownEvents: [anonymous()]}).then(vm => {
      expect(vm.spaceEvents).toHaveLength(2);
    });
  });
});

describe('The slot-picking grid owns up to a calendar it could not read', () => {
  beforeEach(() => {
    global.eXo.env.portal.userIdentityId = '5';
  });

  it('says nothing when both sources answered, even when they answered nothing', () => {
    return readStore({targetEvents: [], ownEvents: []}).then(vm => {
      expect(vm.spaceEvents).toHaveLength(0);
      expect(failureState(vm).hasFailedSource).toBe(false);
    });
  });

  it('names the organiser own calendars when their read failed', () => {
    // Same empty grid as the test above, opposite meaning.
    return readStore({targetEvents: [], ownFails: true}).then(vm => {
      expect(vm.spaceEvents).toHaveLength(0);
      const failure = failureState(vm);
      expect(failure.hasFailedSource).toBe(true);
      expect(failure.names).toEqual(['agenda.eventForm.sourceYourCalendars']);
    });
  });

  it('names the target calendar when its read failed', () => {
    return readStore({targetFails: true, ownEvents: []}).then(vm => {
      expect(failureState(vm).names).toEqual(['agenda.eventForm.sourceThisCalendar']);
    });
  });

  it('keeps what one source returned when the other failed', () => {
    return readStore({targetFails: true, ownEvents: [storeEvent('2', PERSONAL_CALENDAR)]}).then(vm => {
      // One unreachable source must not blank what the other answered.
      expect(vm.spaceEvents.map(event => event.id)).toEqual(['2']);
      expect(failureState(vm).names).toEqual(['agenda.eventForm.sourceThisCalendar']);
    });
  });

  it('names both sources and an unreachable account in one warning', () => {
    return readStore({targetFails: true, ownFails: true}).then(vm => {
      vm.failedConnectors = [{name: 'caldav', user: 'jane@example.com'}];
      expect(failureState(vm).names).toEqual([
        'jane@example.com',
        'agenda.eventForm.sourceThisCalendar',
        'agenda.eventForm.sourceYourCalendars',
      ]);
    });
  });

  it('names both sources when the read throws instead of rejecting', () => {
    // Same gap on this chain: the single-source read had a terminal catch and
    // the two-source rewrite dropped it. Both reads handle their own failure,
    // so reaching here means something else broke — which is not something a
    // grid that becomes a decision may treat as "nothing was in the way".
    const vm = formVm({targetEvents: [], ownEvents: []});
    vm.mergeStoreEvents = () => {
      throw new Error('merge exploded');
    };
    AgendaEventFormDates.methods.retrieveEventsFromStore.call(vm);
    let chain = Promise.resolve();
    for (let step = 0; step < 8; step++) {
      chain = chain.then(() => Promise.resolve());
    }
    return chain.then(() => {
      expect(vm.spaceEvents).toHaveLength(0);
      expect(failureState(vm).names).toEqual([
        'agenda.eventForm.sourceThisCalendar',
        'agenda.eventForm.sourceYourCalendars',
      ]);
    });
  });

  it('stops naming a source once its next read succeeds', () => {
    return readStore({targetFails: true, ownEvents: []}).then(vm => {
      expect(failureState(vm).hasFailedSource).toBe(true);
      // A failure that outlives the read that caused it is a warning nobody
      // can clear.
      vm.$eventService.getEvents = () => Promise.resolve({events: []});
      AgendaEventFormDates.methods.retrieveEventsFromStore.call(vm);
      let chain = Promise.resolve();
      for (let step = 0; step < 8; step++) {
        chain = chain.then(() => Promise.resolve());
      }
      return chain.then(() => expect(failureState(vm).hasFailedSource).toBe(false));
    });
  });
});

describe('The slot-picking grid says which calendar a block is in', () => {
  it('names the calendar beside the event on hover', () => {
    const vm = formVm({});
    expect(vm.storeEventTitle(storeEvent('1', SPACE_CALENDAR))).toBe('agenda.eventForm.eventInCalendar');
  });

  it('keeps the plain summary for a block that carries no calendar', () => {
    const vm = formVm({});
    expect(vm.storeEventTitle({summary: 'A slot being dragged'})).toBe('A slot being dragged');
  });
});
