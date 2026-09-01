import {mount} from '@vue/test-utils';
import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaEventForm from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventForm.vue';
import AgendaEventQuickFormDrawer from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventQuickFormDrawer.vue';
import AgendaEventFormDates from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDates.vue';

/*
 * EXO-89852: the date poll was unfindable, and unexplained once found.
 *
 * Poll mode was implicit in the slot count — one slot made an event, two made
 * a poll — and no word on the path said so: a greyed "Alternative dates"
 * button, a "Suggest dates" step, a "Schedule" save. The quick-add drawer, the
 * entry point a calendar click actually opens, carried no trace of the feature
 * at all.
 *
 * These pins are on what a user would notice, not on the strings:
 *
 *   1. The drawer's new route is open exactly when the form's own next-step
 *      rule is open. Not "similar to it" — the same rule object. A drawer that
 *      restated the rule would let somebody into a step the form refuses to
 *      leave, and the failure only shows on the case the restatement forgot.
 *   2. That route asks for the date step, and the drawer's older "More
 *      details" route does not.
 *   3. The step-2 hint is on screen while the grid is empty and gone once a
 *      slot exists. It is an instruction, so obeying it must remove it — that
 *      is what buys it having no dismissal and no stored state.
 *   4. The save button names what is about to be sent, and follows the option
 *      count: one date creates an event, two or more send a poll.
 */

/**
 * The portal's HTML-to-text helper, as $utils supplies it. Tags out, text in —
 * enough for the length bound the description rule applies.
 *
 * @param {String} html the description
 * @returns {String} its text
 */
function htmlToText(html) {
  return (html || '').replace(/<[^>]*>/g, '');
}

const UTILS = {htmlToText};

/**
 * An event as either screen holds one mid-edit.
 *
 * @param {Object} overrides what this case changes
 * @returns {Object} the event
 */
function editedEvent(overrides) {
  return Object.assign({
    summary: 'Sprint review',
    calendar: {owner: {id: '42'}},
    attendees: [],
    dateOptions: [],
  }, overrides);
}

/*
 * The cases the two screens have to agree on. Each is a way the details step
 * can be incomplete, plus the one way it can be complete — including the
 * description bound, which the drawer has no field for and could very
 * plausibly have been left out of a restated rule.
 */
const AGREEMENT_CASES = [
  ['everything filled in', editedEvent(), true],
  ['no title', editedEvent({summary: ''}), false],
  ['a title one character under the maximum', editedEvent({summary: 'x'.repeat(1023)}), true],
  ['a title of exactly the maximum length', editedEvent({summary: 'x'.repeat(1024)}), false],
  ['no destination', editedEvent({calendar: {owner: {}}}), false],
  ['a destination named by provider and remote id', editedEvent({calendar: {owner: {providerId: 'space', remoteId: 'marketing'}}}), true],
  ['a destination named by provider alone', editedEvent({calendar: {owner: {providerId: 'space'}}}), false],
  ['a description past its bound', editedEvent({description: `<p>${'d'.repeat(1301)}</p>`}), false],
  ['a description at its bound', editedEvent({description: `<p>${'d'.repeat(1300)}</p>`}), true],
];

describe('EXO-89852 — the one rule both screens read', () => {
  AGREEMENT_CASES.forEach(([name, event, complete]) => {
    it(`${complete ? 'accepts' : 'refuses'} an event with ${name}`, () => {
      expect(agendaUtils.isEventDetailsComplete(event, htmlToText)).toBe(complete);
    });
  });
});

/*
 * The details step, as a component rather than an unknown element: the form's
 * stepper watcher calls reset() on it through a ref, and a ref to an unknown
 * element is a DOM node with no such method. Everything else is left to render
 * as the unknown elements the other specs here rely on.
 */
const BASIC_INFORMATION_STUB = {
  render(createElement) {
    return createElement('div');
  },
  methods: {
    reset() {},
    validateForm() {
      return true;
    },
  },
};

/**
 * Mounts the full event form the way the dialog does.
 *
 * @param {Object} options `{event, displayTimeInForm, openDateOptions}`
 * @returns {Object} the mounted wrapper
 */
function mountForm(options) {
  return mount(AgendaEventForm, {
    propsData: {
      event: options.event || editedEvent(),
      displayTimeInForm: !!options.displayTimeInForm,
      openDateOptions: !!options.openDateOptions,
    },
    stubs: {
      'agenda-event-form-basic-information': BASIC_INFORMATION_STUB,
    },
    mocks: {
      $agendaUtils: agendaUtils,
      $utils: UTILS,
      $vuetify: {rtl: false},
      $t: key => key,
    },
  });
}

/**
 * Mounts the quick-add drawer with an event already being edited.
 *
 * Given a parent so that $root is somebody else, the way it is in the running
 * app: the drawer reads $root.isMobile, and a component mounted as its own
 * root could never answer that question truthfully.
 *
 * @param {Object} event what the organiser has typed so far
 * @param {Boolean} mobile whether the app is running on a phone
 * @returns {Object} the mounted wrapper
 */
function mountDrawer(event, mobile) {
  return mount(AgendaEventQuickFormDrawer, {
    data() {
      return {event};
    },
    parentComponent: {
      data() {
        return {isMobile: !!mobile};
      },
    },
    mocks: {
      $agendaUtils: agendaUtils,
      $utils: UTILS,
      $t: key => key,
    },
  });
}

describe('EXO-89852 — the drawer admits exactly whom the form admits', () => {
  AGREEMENT_CASES.forEach(([name, event]) => {
    it(`agrees with the form's next-step rule on ${name}`, () => {
      const form = mountForm({event, displayTimeInForm: true});
      const drawer = mountDrawer(event);

      expect(drawer.vm.datePollRouteEnabled).toBe(!form.vm.disableNextStepButton);
    });
  });

  it('closes the route while the drawer is saving, though the form would allow the step', () => {
    const event = editedEvent();
    const form = mountForm({event, displayTimeInForm: true});
    const drawer = mountDrawer(event);
    drawer.setData({saving: true});

    expect(form.vm.disableNextStepButton).toBe(false);
    expect(drawer.vm.datePollRouteEnabled).toBe(false);
  });

  it('shows the route as a link when it is open, and greys the same words when it is not', () => {
    const open = mountDrawer(editedEvent());
    const shut = mountDrawer(editedEvent({summary: ''}));

    expect(open.text()).toContain('agenda.alternativeDates');
    expect(open.find('a.primary--text').exists()).toBe(true);

    expect(shut.find('a.primary--text').exists()).toBe(false);
    expect(shut.text()).toContain('agenda.alternativeDates');
  });

  /*
   * The disabled link says nothing beyond its own name: the two fields it
   * waits on sit directly above it. Asserted as "no date-poll message key
   * renders here at all" rather than against the retired key, so a sentence
   * reintroduced under any new name fails this too.
   */
  it('says nothing further when it is disabled, whatever the sentence might be called', () => {
    const shut = mountDrawer(editedEvent({summary: ''}));

    expect(shut.text()).not.toContain('agenda.datePoll.');
    expect(shut.findAll('.text-light-color').length).toBe(1);
  });

  it('offers nothing on mobile, where the form it would open has no date step', () => {
    const onPhone = mountDrawer(editedEvent(), true);

    expect(onPhone.text()).not.toContain('agenda.alternativeDates');
    expect(onPhone.text()).not.toContain('agenda.datePoll.');
    expect(onPhone.text()).toContain('agenda.button.moreDetails');
  });
});

/**
 * The body's rows, in the order the DOM holds them.
 *
 * Read off the rendered container's direct children rather than off the
 * source, and each row is named by something only that row contains — so a row
 * appended in the wrong place, or moved, shows up here as a changed sequence.
 * A row this function does not recognise is reported rather than skipped: a
 * future field silently dropping in unnamed would otherwise pass.
 *
 * @param {Object} drawer the mounted drawer
 * @returns {Array} the row names, top to bottom
 */
function bodyRowOrder(drawer) {
  const body = drawer.element.querySelector('.flex-column.flex-grow-1');
  return Array.prototype.map.call(body.children, row => {
    if (row.querySelector('#eventTitle')) {
      return 'title';
    } else if (row.querySelector('agenda-event-form-destination')) {
      return 'destination';
    } else if (row.querySelector('agenda-event-form-date-pickers')) {
      return 'date';
    } else if (row.textContent.includes('agenda.alternativeDates')) {
      return 'datePollLink';
    } else if (row.querySelector('agenda-event-form-attendees')) {
      return 'participants';
    } else if (row.querySelector('#eventLocation')) {
      return 'location';
    } else if (row.tagName.toLowerCase() === 'agenda-event-form-conference') {
      return 'conference';
    } else if (row.querySelector('agenda-event-form-color-picker')) {
      return 'colour';
    }
    return `unrecognised:${row.tagName.toLowerCase()}`;
  });
}

/*
 * EXO-89852, the drawer's running order. It grew by appending — nothing chose
 * it — and left the two rows that carry DECISIONS below the three that carry
 * attributes of a meeting already decided.
 *
 * Who is coming is the same kind of choice as when: it is what decides whether
 * the time works, which is why Google and Outlook both put guests straight
 * after the time. Location, conference and colour are decoration by
 * comparison. And the date-poll link answers a question asked while looking at
 * the time field — past the optional attributes it reads as one more action
 * rather than as an alternative to the field above it.
 *
 * Geometry is not testable here, but sequence is, and sequence is the whole
 * of the change.
 */
describe('EXO-89852 — the drawer asks for the decisions first', () => {
  it('puts the date-poll link under the time and the participants above the optional fields', () => {
    const order = bodyRowOrder(mountDrawer(editedEvent()));

    expect(order).toEqual([
      'title',
      'destination',
      'date',
      'datePollLink',
      'participants',
      'location',
      'conference',
      'colour',
    ]);
  });

  /*
   * The two relationships the order exists for, asserted by name so a failure
   * says which one broke rather than only that the list changed.
   */
  it('keeps the link immediately after the date pickers, and participants above location', () => {
    const order = bodyRowOrder(mountDrawer(editedEvent()));

    expect(order[order.indexOf('date') + 1]).toBe('datePollLink');
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('location'));
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('conference'));
    expect(order.indexOf('participants')).toBeLessThan(order.indexOf('colour'));
  });

  /*
   * The disabled link holds its place: it is greyed, not removed, so the rows
   * below it do not shift under an organiser who has not filled the title in
   * yet.
   */
  it('holds the link\'s place while it is disabled', () => {
    expect(bodyRowOrder(mountDrawer(editedEvent({summary: ''})))[3]).toBe('datePollLink');
  });

  /*
   * On mobile the link is absent (the form behind it cannot make a poll), and
   * its absence must not disturb anything else.
   */
  it('drops only the link on mobile, leaving the rest of the order alone', () => {
    const order = bodyRowOrder(mountDrawer(editedEvent(), true));

    expect(order).toEqual([
      'title',
      'destination',
      'date',
      'participants',
      'location',
      'conference',
      'colour',
    ]);
  });
});

describe('EXO-89852 — the route asks for the date step', () => {
  /**
   * Captures what the drawer emits on the root bus.
   *
   * @param {Object} drawer the mounted drawer
   * @returns {Array} the arguments of each agenda-event-form emission
   */
  function formEmissions(drawer) {
    const emitted = [];
    drawer.vm.$root.$on('agenda-event-form', (...args) => emitted.push(args));
    return emitted;
  }

  it('asks the dialog to open on the date step, and the older route does not', () => {
    const drawer = mountDrawer(editedEvent({startDate: new Date('2026-09-01T09:00:00Z'), endDate: new Date('2026-09-01T10:00:00Z')}));
    const emitted = formEmissions(drawer);

    drawer.vm.openDatePollForm();
    expect(emitted[0][2]).toBe(true);

    drawer.setData({event: editedEvent({startDate: new Date('2026-09-01T09:00:00Z'), endDate: new Date('2026-09-01T10:00:00Z')})});
    drawer.vm.openCompleteEventForm();
    expect(emitted[1][2]).toBe(false);
  });

  it('lands the form on the date step when asked, and on the details step otherwise', () => {
    const onDates = mountForm({displayTimeInForm: true, openDateOptions: true});
    const onDetails = mountForm({displayTimeInForm: true});

    return onDates.vm.$nextTick()
      .then(() => onDetails.vm.$nextTick())
      .then(() => {
        expect(onDates.vm.stepper).toBe(2);
        expect(onDetails.vm.stepper).toBe(1);
      });
  });
});

describe('EXO-89852 — the save button says what it is about to send', () => {
  it('sends a date poll on two options and creates an event on one', () => {
    const form = mountForm({displayTimeInForm: true});

    form.setData({eventDateOptionsLength: 2});
    expect(form.vm.saveButtonLabel).toBe('agenda.label.schedule');

    form.setData({eventDateOptionsLength: 1});
    expect(form.vm.saveButtonLabel).toBe('agenda.label.create');
  });

  it('keeps saying save when an existing event is being edited', () => {
    const form = mountForm({event: editedEvent({id: '7'}), displayTimeInForm: true});

    form.setData({eventDateOptionsLength: 1});
    expect(form.vm.saveButtonLabel).toBe('agenda.label.save');
  });

  it('explains the route beside the button that takes it, and only while that button is there', () => {
    const onDetails = mountForm({displayTimeInForm: true});

    return onDetails.vm.$nextTick().then(() => {
      expect(onDetails.vm.footerHint).toBe('agenda.datePoll.explanation');
      expect(onDetails.text()).toContain('agenda.datePoll.explanation');

      onDetails.setData({stepper: 2});
      return onDetails.vm.$nextTick().then(() => {
        expect(onDetails.text()).not.toContain('agenda.datePoll.explanation');
      });
    });
  });
});

describe('EXO-89852 — the date step is instructed from the footer', () => {
  /**
   * Puts the form on the date step with a given number of slots on the grid.
   *
   * The slots go on the EVENT, never straight into eventDateOptionsLength:
   * the stepper watcher recounts them off the event on arrival, so a count
   * planted in data would be overwritten and the test would be asserting
   * against a state the running form can never be in.
   *
   * @param {Number} slots how many date options the organiser has dragged
   * @returns {Promise} resolves with the mounted wrapper
   */
  function onDateStep(slots) {
    const dateOptions = [];
    for (let slot = 0; slot < slots; slot++) {
      dateOptions.push({allDay: false});
    }
    const form = mountForm({event: editedEvent({dateOptions}), displayTimeInForm: true});
    return form.vm.$nextTick().then(() => {
      form.setData({stepper: 2});
      return form.vm.$nextTick().then(() => form);
    });
  }

  it('carries the instruction in the footer while the grid is empty', () => {
    return onDateStep(0).then(form => {
      expect(form.vm.footerHint).toBe('agenda.datePoll.dragHint');
      expect(form.text()).toContain('agenda.datePoll.dragHint');
      expect(form.find('v-stepper').text()).not.toContain('agenda.datePoll.dragHint');
    });
  });

  /*
   * One slot is not the state this hint is spent by. The instruction stands
   * until the event becomes a poll, and it goes exactly as the save button
   * turns into "Send date poll" — the two read the same count, so a screen
   * offering the instruction while the button already says the work is done
   * is not reachable.
   */
  it('stands while one slot makes an ordinary event, and goes at the second', () => {
    return onDateStep(1).then(form => {
      expect(form.vm.footerHint).toBe('agenda.datePoll.dragHint');
      expect(form.vm.saveButtonLabel).toBe('agenda.label.create');
      expect(form.text()).toContain('agenda.datePoll.dragHint');

      return onDateStep(2).then(poll => {
        expect(poll.vm.footerHint).toBe('');
        expect(poll.vm.saveButtonLabel).toBe('agenda.label.schedule');
        expect(poll.text()).not.toContain('agenda.datePoll.dragHint');
      });
    });
  });

  /*
   * The defect that made the guard wrong in the first place, pinned through
   * the path that exposed it rather than through a planted count: opened from
   * the quick-add drawer, the clicked slot is ALREADY on the grid before the
   * organiser sees the step. That path is the whole audience for this line —
   * somebody who got to the date step without knowing the feature is there —
   * and a guard on an empty grid silently withheld it from exactly them.
   */
  it('greets the organiser arriving from the quick-add drawer, whose slot is already on the grid', () => {
    const fromDrawer = mountForm({
      event: editedEvent({
        startDate: new Date('2026-09-01T09:00:00Z'),
        endDate: new Date('2026-09-01T10:00:00Z'),
      }),
      displayTimeInForm: true,
      openDateOptions: true,
    });

    return fromDrawer.vm.$nextTick()
      .then(() => fromDrawer.vm.$nextTick())
      .then(() => {
        expect(fromDrawer.vm.stepper).toBe(2);
        expect(fromDrawer.vm.eventDateOptionsLength).toBe(1);
        expect(fromDrawer.vm.footerHint).toBe('agenda.datePoll.dragHint');
        expect(fromDrawer.text()).toContain('agenda.datePoll.dragHint');
      });
  });

  /*
   * The step it was moved OUT of must not keep a copy: two copies would both
   * be right on the day of the move and drift the first time either changes.
   * Asserted at both counts the footer now renders at, so a leftover copy
   * cannot hide behind whichever one the threshold happens to be.
   */
  it('is gone from the grid it used to sit above', () => {
    expect(renderDatesStep([])).not.toContain('agenda.datePoll.dragHint');
    expect(renderDatesStep([{allDay: false}])).not.toContain('agenda.datePoll.dragHint');
  });
});

/**
 * Renders the date step's own template over a grid holding the given slots.
 *
 * The component is mounted with its mounted() hook neutralised: that hook
 * reaches into the Vuetify calendar for pixel positions, and Vuetify is not a
 * devDependency here — the same reason every other spec in this suite lets the
 * v-* elements render as unknown custom elements. Nothing else is stubbed, so
 * what is asserted is the shipped template's own guard.
 *
 * @param {Array} dateOptions the slots already on the grid
 * @returns {String} the rendered text
 */
function renderDatesStep(dateOptions) {
  const DatesStep = Object.assign({}, AgendaEventFormDates, {mounted() {}});
  const wrapper = mount(DatesStep, {
    propsData: {
      event: editedEvent({dateOptions, status: 'TENTATIVE'}),
      settings: {},
      connectors: [],
      weekdays: [1, 2, 3, 4, 5, 6, 0],
      workingTime: null,
    },
    mocks: {
      $agendaUtils: agendaUtils,
      $utils: UTILS,
      $vuetify: {rtl: false},
      $t: key => key,
      $eventService: {getEvents: () => Promise.resolve({events: []})},
      $availabilityService: {getBusyTime: () => Promise.resolve({})},
      $identityService: {getIdentityByProviderIdAndRemoteId: () => Promise.resolve(null)},
    },
  });
  return wrapper.text();
}
