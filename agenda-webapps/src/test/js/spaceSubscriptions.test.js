/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import fs from 'fs';
import path from 'path';
import Vue from 'vue';
import {shallowMount} from '@vue/test-utils';

import AgendaSpaceSubscriptionsDrawer from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaSpaceSubscriptionsDrawer.vue';
import AgendaCalendarSubscriptionDrawer from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaCalendarSubscriptionDrawer.vue';
import AgendaFilterCalendarItem from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue';
import AgendaEventsDetailsBody from '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue';
import AgendaEventDetailsToolbar from '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventDetailsToolbar.vue';
import AgendaEventDetailsMobileToolbar from '../../main/webapp/vue-app/agenda-common/components/event/view/mobile/AgendaEventDetailsMobileToolbar.vue';
import * as subscriptionService from '../../main/webapp/vue-app/agenda-common/js/CalendarSubscriptionService.js';

/**
 * A space's subscribed calendars (EXO-90373): the managers' drawer — its list,
 * who added each and when, the last refresh, the withdrawn-link warning,
 * Refresh now and Remove — the add drawer in its space mode, the entry of the
 * space calendar's menu, the space settings row, and an event of such a
 * calendar reading as the space's with one line naming its calendar.
 *
 * <p>Vuetify is not part of the agenda build: its elements are inert here, so
 * the tests call the components' methods and read what they render.</p>
 */
describe('A space subscribes to calendars', () => {

  Vue.config.ignoredElements = [/^v-(?!menu$)/];

  const SPACE = {ownerId: 100, spaceName: 'Chemistry'};

  const VMenuStub = {
    name: 'VMenuStub',
    props: ['value', 'contentClass'],
    template: '<div class="v-menu-stub"><slot /></div>',
  };

  const ROWS = [
    {id: 11, calendarId: 77, name: 'Holidays', creatorFullName: 'John Smith', creatorUsername: 'john',
      createdDate: Date.UTC(2026, 8, 1), lastSuccessDate: Date.UTC(2026, 8, 17, 8, 0), lastError: null},
    {id: 12, calendarId: 78, name: 'Seminars', creatorFullName: 'Anne Doe', creatorUsername: 'anne',
      createdDate: Date.UTC(2026, 8, 2), lastSuccessDate: 0, lastError: 'agenda.calendarSubscription.linkNotFound'},
    {id: 13, calendarId: 79, name: 'Lab', creatorFullName: 'Anne Doe', creatorUsername: 'anne',
      createdDate: Date.UTC(2026, 8, 3), lastSuccessDate: 0, lastError: 'agenda.calendarSubscription.unreachable'},
  ];

  const bundle = fs.readFileSync(path.resolve(__dirname, '../../main/resources/locale/portlet/Agenda_en.properties'), 'utf8');

  const $t = (key, args) => args && `${key}(${Object.values(args).join('|')})` || key;

  let service;

  let drawerStub;

  let confirmStub;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the managers' drawer and opens it on the space.
   *
   * @returns {Promise<Object>} the wrapper
   */
  async function openSpaceDrawer() {
    const wrapper = shallowMount(AgendaSpaceSubscriptionsDrawer, {
      attachTo: document.body,
      mocks: {$t, $calendarSubscriptionService: service},
      stubs: {'exo-drawer': drawerStub, 'exo-confirm-dialog': confirmStub, 'v-menu': VMenuStub},
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    wrapper.vm.$root.$emit('agenda-space-subscriptions-drawer-open', {...SPACE});
    await flush();
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
  });

  beforeEach(() => {
    service = {
      getSubscriptions: jest.fn().mockResolvedValue(ROWS.map(row => ({...row}))),
      checkUrl: jest.fn(),
      createSubscription: jest.fn(),
      refreshSubscription: jest.fn(),
      deleteSubscription: jest.fn(),
    };
    drawerStub = {
      template: '<div class="drawer-stub"><slot name="title"></slot><slot name="content"></slot><slot name="footer"></slot></div>',
      methods: {open: jest.fn(), close: jest.fn()},
    };
    confirmStub = {
      template: '<div class="confirm-stub"></div>',
      methods: {open: jest.fn()},
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  describe('the managers\' drawer', () => {

    it('opens on its root event and lists the space\'s calendars with who added them and the last refresh', async () => {
      const wrapper = await openSpaceDrawer();

      expect(drawerStub.methods.open).toHaveBeenCalled();
      expect(service.getSubscriptions).toHaveBeenCalledWith(100);
      const rows = wrapper.findAll('.agenda-space-subscription');
      expect(rows).toHaveLength(3);
      expect(rows.at(0).find('.agenda-space-subscription-name').text()).toContain('Holidays');
      expect(rows.at(0).find('.agenda-space-subscription-added').text())
        .toBe(`agenda.calendarSubscription.addedBy(John Smith|${wrapper.vm.formatDate(ROWS[0].createdDate, false)})`);
      expect(rows.at(0).find('.agenda-space-subscription-refreshed').text())
        .toBe(`agenda.calendarSubscription.lastRefresh(${wrapper.vm.formatDate(ROWS[0].lastSuccessDate, true)})`);
      expect(rows.at(1).find('.agenda-space-subscription-refreshed').text()).toBe('agenda.calendarSubscription.neverRefreshed');
      expect(wrapper.find('.agenda-space-subscriptions-empty').exists()).toBe(false);
    });

    it('warns in words of its own about a withdrawn link, and gives the reason of any other failure', async () => {
      const wrapper = await openSpaceDrawer();
      const rows = wrapper.findAll('.agenda-space-subscription');

      expect(rows.at(0).find('.agenda-space-subscription-warning').exists()).toBe(false);
      expect(rows.at(1).find('.agenda-space-subscription-warning').text()).toContain('agenda.calendarSubscription.withdrawn');
      expect(rows.at(2).find('.agenda-space-subscription-warning').text())
        .toContain('agenda.calendarSubscription.neverImported(agenda.calendarSubscription.unreachable)');
      expect(rows.at(2).find('.agenda-space-subscription-warning').text()).not.toContain('agenda.calendarSubscription.withdrawn');
    });

    /*
     * EXO-90402: the week-long cleanup is promised to the one case the product
     * performs it for. Only a withdrawn link of this eXo purges; an external
     * address that stops answering is retried for ever and keeps its events,
     * and the two now have a sentence each.
     */
    it('promises the week-long cleanup only for a withdrawn eXo link, and says an unanswered address keeps its events', async () => {
      service.getSubscriptions.mockResolvedValue([
        // A withdrawn eXo link that did import once: its copy goes
        {id: 21, calendarId: 81, name: 'Company', creatorFullName: 'Anne Doe', creatorUsername: 'anne',
          createdDate: Date.UTC(2026, 7, 1), lastSuccessDate: Date.UTC(2026, 8, 10, 6, 0),
          lastError: 'agenda.calendarSubscription.linkNotFound'},
        // An external address that stopped answering after importing: its copy stays
        {id: 22, calendarId: 82, name: 'Conferences', creatorFullName: 'Anne Doe', creatorUsername: 'anne',
          createdDate: Date.UTC(2026, 7, 2), lastSuccessDate: Date.UTC(2026, 8, 11, 7, 30),
          lastError: 'agenda.calendarSubscription.timeout'},
      ]);
      const wrapper = await openSpaceDrawer();
      const rows = wrapper.findAll('.agenda-space-subscription');

      const withdrawn = rows.at(0).find('.agenda-space-subscription-warning').text();
      expect(withdrawn).toContain('agenda.calendarSubscription.withdrawn');
      expect(withdrawn).not.toContain('agenda.calendarSubscription.stillImported');

      const unanswered = rows.at(1).find('.agenda-space-subscription-warning').text();
      expect(unanswered).toContain(`agenda.calendarSubscription.stillImported(agenda.calendarSubscription.timeout|${
        wrapper.vm.formatDate(Date.UTC(2026, 8, 11, 7, 30), true)})`);
      expect(unanswered).not.toContain('agenda.calendarSubscription.withdrawn');
    });

    it('ships an English sentence per case: a week for the withdrawn link, events kept for the address', () => {
      const withdrawn = bundle.match(/^agenda\.calendarSubscription\.withdrawn=(.*)$/m)[1];
      const stillImported = bundle.match(/^agenda\.calendarSubscription\.stillImported=(.*)$/m)[1];
      const neverImported = bundle.match(/^agenda\.calendarSubscription\.neverImported=(.*)$/m)[1];

      expect(withdrawn).toContain('removed a week after the last refresh');
      // The cleanup is never promised where it does not happen
      expect(stillImported).not.toContain('week');
      expect(stillImported).not.toContain('removed');
      expect(stillImported).toContain('Nothing has been imported since {1}');
      expect(stillImported).toContain('stay as they are');
      expect(neverImported).not.toContain('week');
      expect(neverImported).toContain('shows no events');
    });

    it('says the space has none, and shows a refused listing in place', async () => {
      service.getSubscriptions.mockResolvedValue([]);
      let wrapper = await openSpaceDrawer();
      expect(wrapper.find('.agenda-space-subscriptions-empty').exists()).toBe(true);

      service.getSubscriptions.mockRejectedValue(new Error('agenda.calendarSubscription.forbidden'));
      wrapper = await openSpaceDrawer();
      expect(wrapper.find('.agenda-space-subscriptions-load-error').text()).toBe('agenda.calendarSubscription.forbidden');
    });

    it('opens the add drawer for this space, and lists again once a calendar was added to it', async () => {
      const wrapper = await openSpaceDrawer();

      wrapper.vm.add();
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-subscription-drawer-open', null, SPACE);

      service.getSubscriptions.mockClear();
      wrapper.vm.$root.$emit('agenda-space-subscriptions-changed', 200);
      await flush();
      expect(service.getSubscriptions).not.toHaveBeenCalled();
      wrapper.vm.$root.$emit('agenda-space-subscriptions-changed', 100);
      await flush();
      expect(service.getSubscriptions).toHaveBeenCalledWith(100);
    });

    it('refreshes a row now: the row takes the answer and the agenda reads the events again', async () => {
      service.refreshSubscription.mockResolvedValue({...ROWS[1], lastError: 'agenda.calendarSubscription.linkNotFound'});
      const wrapper = await openSpaceDrawer();

      await wrapper.vm.refresh(wrapper.vm.subscriptions[1]);

      expect(service.refreshSubscription).toHaveBeenCalledWith(12);
      expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarSubscription.withdrawn', 'warning');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh');
      expect(wrapper.vm.refreshingIds).toEqual([]);
    });

    it('removes a row only once confirmed', async () => {
      service.deleteSubscription.mockResolvedValue(null);
      const wrapper = await openSpaceDrawer();

      wrapper.vm.confirmRemove(wrapper.vm.subscriptions[0]);
      expect(confirmStub.methods.open).toHaveBeenCalled();
      expect(service.deleteSubscription).not.toHaveBeenCalled();
      expect(wrapper.vm.removeMessage).toBe('agenda.calendarSubscription.removeConfirmMessage(Holidays|Chemistry)');

      await wrapper.vm.remove();
      expect(service.deleteSubscription).toHaveBeenCalledWith(11);
      expect(wrapper.vm.subscriptions.map(row => row.id)).toEqual([12, 13]);
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-space-subscriptions-removed', 100);
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh');
    });

  });

  describe('the add drawer for a space', () => {

    /**
     * Mounts the add drawer.
     *
     * @returns {Object} the wrapper
     */
    function mountAddDrawer() {
      const wrapper = shallowMount(AgendaCalendarSubscriptionDrawer, {
        mocks: {$t, $calendarSubscriptionService: service, $agendaUtils: {EVENT_COLOR_SWATCHES: [['#08a554']]}},
        stubs: {'exo-drawer': drawerStub},
      });
      wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
      return wrapper;
    }

    it('explains the space, draws no colour field, and checks the link for the space', async () => {
      service.checkUrl.mockResolvedValue({name: 'Holidays'});
      const wrapper = mountAddDrawer();
      wrapper.vm.$root.$emit('agenda-calendar-subscription-drawer-open', null, {...SPACE});
      await wrapper.vm.$nextTick();

      expect(wrapper.find('.agenda-calendar-subscription-explanation').text())
        .toBe('agenda.calendarSubscription.explanationSpace(Chemistry)');
      expect(wrapper.find('.agenda-calendar-subscription-color').exists()).toBe(false);
      await wrapper.setData({url: 'https://feeds.example.org/holidays.ics'});
      await wrapper.vm.check();
      expect(service.checkUrl).toHaveBeenCalledWith('https://feeds.example.org/holidays.ics', 100);
    });

    it('adds the calendar to the space with no colour, and tells the managers\' drawer rather than the personal section', async () => {
      service.createSubscription.mockResolvedValue({id: 14, name: 'Holidays'});
      const wrapper = mountAddDrawer();
      wrapper.vm.open(null, {...SPACE});
      await wrapper.setData({url: 'https://feeds.example.org/holidays.ics', color: '#112233'});

      await wrapper.vm.save();
      await flush();

      expect(service.createSubscription).toHaveBeenCalledWith({url: 'https://feeds.example.org/holidays.ics', name: null, color: null, ownerId: 100});
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-space-subscriptions-changed', 100);
      expect(wrapper.rootEmit).not.toHaveBeenCalledWith('agenda-refresh-subscribed-calendars');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarSubscription.subscribedSpace(Holidays|Chemistry)', 'success');
    });

    it('opened again for the user, is the personal drawer again', async () => {
      service.createSubscription.mockResolvedValue({id: 15, name: 'Mine'});
      const wrapper = mountAddDrawer();
      wrapper.vm.open(null, {...SPACE});
      wrapper.vm.open();
      await wrapper.setData({url: 'https://feeds.example.org/mine.ics', color: '#112233'});

      expect(wrapper.find('.agenda-calendar-subscription-color').exists()).toBe(true);
      await wrapper.vm.save();
      expect(service.createSubscription).toHaveBeenCalledWith({url: 'https://feeds.example.org/mine.ics', name: null, color: '#112233'});
    });

    it('says a link is to a calendar of the space itself', () => {
      expect(subscriptionService.errorMessageKey('agenda.calendarSubscription.ownSpaceCalendar')).toBe('agenda.calendarSubscription.ownSpaceCalendar');
      expect(bundle).toMatch(/^agenda\.calendarSubscription\.ownSpaceCalendar=.+$/m);
    });

  });

  describe('the entry points', () => {

    /**
     * Mounts a space calendar row.
     *
     * @param {Boolean} manager whether the user is a real manager of the space
     * @returns {Promise<Object>} the wrapper
     */
    async function mountRow(manager) {
      const wrapper = shallowMount(AgendaFilterCalendarItem, {
        propsData: {
          calendar: {id: 22, owner: {id: 100, providerId: 'space', space: {displayName: 'Chemistry'}}, acl: {canEdit: manager, canPublish: manager}},
          ownerIds: [100],
          selectedOwnerIds: [],
        },
        mocks: {$t, $calendarLinkService: {getCalendarLinks: jest.fn().mockResolvedValue([])}},
        stubs: {'v-menu': VMenuStub},
      });
      wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
      await flush();
      return wrapper;
    }

    it('offers a real manager the space\'s subscribed calendars in the space calendar\'s menu', async () => {
      const wrapper = await mountRow(true);

      await wrapper.find('.agenda-space-subscriptions-action').trigger('click');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-space-subscriptions-drawer-open', {ownerId: 100, spaceName: 'Chemistry'});
    });

    it('offers nothing to a member', async () => {
      const wrapper = await mountRow(false);

      expect(wrapper.find('.agenda-space-subscriptions-action').exists()).toBe(false);
    });

    it('opens the drawer from the settings page\'s own edit action, drawn as its sibling rows draw theirs', () => {
      const settings = fs.readFileSync(path.resolve(__dirname,
                                                    '../../main/webapp/vue-app/agenda-space-administration/components/AgendaSpaceAdministration.vue'),
                                       'utf8');
      const row = settings.slice(settings.indexOf('agenda-space-subscriptions-setting'),
                                 settings.indexOf('</v-list>', settings.indexOf('agenda-space-subscriptions-setting')));

      // social's SpaceSettingAccess / SpaceSettingCategories / SpaceSettingPublicSite
      // / SpaceSettingSubspaces all draw this one control, and this page is theirs
      expect(row).toMatch(/<v-btn\s+:title="\$t\('agenda\.space\.settings\.subscriptions\.button\.tooltip'\)"\s+small\s+icon/);
      expect(row).toContain('<v-icon size="18" class="icon-default-color">fa-edit</v-icon>');
      expect(row).toContain('@click="openSubscriptions"');
      expect(row).not.toContain('agenda.space.settings.subscriptions.manage');
      expect(row).toContain('agenda.space.settings.subscriptions.title');
      expect(row).toContain('agenda.space.settings.subscriptions.count');
      expect(bundle).not.toMatch(/^agenda\.space\.settings\.subscriptions\.manage=/m);
    });

    it('is mounted once in the agenda and once in the space settings, where the common module is loaded', () => {
      const read = file => fs.readFileSync(path.resolve(__dirname, '../../main/webapp', file), 'utf8');
      const agenda = read('vue-app/agenda/components/Agenda.vue');
      const settings = read('vue-app/agenda-space-administration/components/AgendaSpaceAdministration.vue');
      const resources = read('WEB-INF/gatein-resources.xml');

      expect(agenda.match(/<agenda-space-subscriptions-drawer\b/g)).toHaveLength(1);
      expect(settings.match(/<agenda-space-subscriptions-drawer\b/g)).toHaveLength(1);
      expect(settings.match(/<agenda-calendar-subscription-drawer\b/g)).toHaveLength(1);
      const settingsModule = resources.slice(resources.indexOf('<name>AgendaSpaceSettingExtension</name>'));
      expect(settingsModule.slice(0, settingsModule.search(/\n {2}<\/module>/))).toContain('<module>AgendaCommon</module>');
    });

  });

  describe('an event of a calendar the space subscribes to', () => {

    const spaceEvent = {calendar: {name: 'Holidays', subscription: true, owner: {providerId: 'space', space: {displayName: 'Chemistry'}}}};

    const personalEvent = {calendar: {name: 'Holidays', subscription: true, owner: {providerId: 'organization', profile: {fullname: 'John'}}}};

    /**
     * Evaluates the calendar label of a details component over an event.
     *
     * @param {Object} component the component
     * @param {Object} event the event
     * @returns {Object} whether it is a space's subscribed event, and its label
     */
    function label(component, event) {
      const context = {event, $t};
      context.owner = event.calendar.owner;
      context.ownerSpace = context.owner.space;
      context.ownerUserProfile = context.owner.profile;
      context.ownerProfile = context.owner.profile || context.owner.space;
      context.isSpaceSubscription = component.computed.isSpaceSubscription.call(context);
      return {spaceSubscription: context.isSpaceSubscription, title: component.computed.ownerDisplayName.call(context), context};
    }

    it.each([
      ['the details body', AgendaEventsDetailsBody],
      ['the toolbar', AgendaEventDetailsToolbar],
      ['the mobile toolbar', AgendaEventDetailsMobileToolbar],
    ])('reads as the space in %s, while a personal subscription keeps its name', (name, component) => {
      expect(label(component, spaceEvent)).toMatchObject({spaceSubscription: true, title: 'Chemistry'});
      expect(label(component, personalEvent)).toMatchObject({spaceSubscription: false, title: 'Holidays'});
    });

    it('says which calendar it comes from, in one line of the details', () => {
      const {context} = label(AgendaEventsDetailsBody, spaceEvent);
      const body = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue'), 'utf8');

      expect(AgendaEventsDetailsBody.computed.spaceSubscriptionLabel.call(context))
        .toBe('agenda.calendarSubscription.fromSpaceFeed(Holidays)');
      expect(body).toMatch(/<div v-if="isSpaceSubscription" class="event-space-subscription[^"]*">/);
      expect(bundle).toMatch(/^agenda\.calendarSubscription\.fromSpaceFeed=From the calendar \{0\}, added to this space$/m);
    });

  });

  describe('what the space agenda asks for', () => {

    const Agenda = require('../../main/webapp/vue-app/agenda/components/Agenda.vue').default;

    /**
     * Calls the agenda's event retrieval over a context standing for a page,
     * and answers the arguments it sent to the events REST.
     *
     * @param {String} eventType the mode the page runs in
     * @param {Number} spaceContextId the space the page is in, or null
     * @returns {Array} the arguments of the one call
     */
    function requestedWith(eventType, spaceContextId) {
      const getEvents = jest.fn().mockResolvedValue({events: []});
      const context = {
        eventsRequestId: 0,
        eventType,
        spaceContextId,
        searchTerm: null,
        limit: 0,
        loading: false,
        initialized: false,
        hasMore: false,
        events: [],
        pageSize: 10,
        filterCanceledEvents: true,
        hiddenOwnCalendarIds: [],
        sharedCalendarIds: [4242],
        period: {start: new Date(Date.UTC(2026, 8, 14)), end: new Date(Date.UTC(2026, 8, 21))},
        leftPanelAvailable: !spaceContextId,
        effectiveOwnerIds: [100],
        $eventService: {getEvents},
        $agendaUtils: {toRFC3339: date => date.toISOString(), toDate: value => new Date(value)},
      };
      Agenda.methods.retrieveEventsFromStore.call(context);
      expect(getEvents).toHaveBeenCalledTimes(1);
      return getEvents.mock.calls[0];
    }

    /**
     * The space agenda is owner-scoped: it sends no attendee, so the attendee
     * row a space subscription writes on each imported event (EXO-90373 - the
     * row that makes those events reach a member's own agenda) can never bring
     * anything extra into the space's own view. What it shows is the events of
     * the space's calendars, the subscribed one included, and nothing else.
     */
    it('asks by owner and never by attendee, so an attendee row adds nothing to it', () => {
      const [, ownerIds, attendeeIdentityId, , , , , , , calendarIds] = requestedWith('allEvents', 42);

      expect(ownerIds).toEqual([100]);
      expect(attendeeIdentityId).toBeNull();
      expect(calendarIds).toEqual([], 'and no shared calendar of the viewer either');
    });

    /**
     * A space's agenda never reads the viewer's connected accounts (EXO-90373):
     * their events are the viewer's own, and merging them drew a second, pale
     * row beside the space's events — the copy that account holds of the very
     * same meeting, in the colour of a collection that has nothing to do with
     * the space.
     */
    it('reads no connected account in a space, and still reads them in the personal agenda', () => {
      const connector = {getEvents: jest.fn().mockResolvedValue({events: []})};
      const context = () => ({
        settingsLoaded: true,
        connectorStatus: 1,
        signedInConnectors: [connector],
        remoteEvents: [{id: 'left-over'}],
        failedConnectors: [{name: 'x'}],
        loading: false,
        period: {start: new Date(Date.UTC(2026, 8, 14)), end: new Date(Date.UTC(2026, 8, 21))},
        $agendaUtils: {
          toRFC3339: date => date.toISOString(),
          toDate: value => new Date(value),
          readConnectorAnswer: answer => ({events: answer.events, failed: false}),
          splitRemoteEventResults: () => ({events: [], failedConnectors: []}),
        },
      });

      const inSpace = {...context(), leftPanelAvailable: false};
      Agenda.methods.retrieveRemoteEvents.call(inSpace);
      expect(connector.getEvents).not.toHaveBeenCalled();
      expect(inSpace.remoteEvents).toEqual([]);
      expect(inSpace.failedConnectors).toEqual([]);

      const personal = {...context(), leftPanelAvailable: true};
      Agenda.methods.retrieveRemoteEvents.call(personal);
      expect(connector.getEvents).toHaveBeenCalledTimes(1);
    });

    /**
     * The personal agenda is attendee-scoped, which is why the import writes
     * the space as an attendee: without that row the feed's events would not
     * reach a member's own agenda at all.
     */
    it('is attendee-scoped in the personal agenda, which is what carries a space subscription there', () => {
      const [, , attendeeIdentityId] = requestedWith('myEvents', null);

      expect(attendeeIdentityId).toBe('1');
    });

  });

  describe('an event of the space\'s subscribed calendar is read-only in the UI', () => {

    const AgendaEventDetails = require('../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventDetails.vue').default;

    /** What the server answers for such an event: nobody attends it, nobody edits it. */
    const feedEvent = {
      id: 224,
      summary: 'alice2shareEvent',
      acl: {canEdit: false, attendee: false},
      attendees: [],
      calendar: {id: 38, name: 'Alice2Share', subscription: true, owner: {id: 10, providerId: 'space', space: {displayName: 'Chemistry'}}},
    };

    /** A space event somebody was really invited to, for contrast. */
    const invitedEvent = {
      id: 226,
      summary: 'eventinericspace',
      acl: {canEdit: true, attendee: true},
      attendees: [],
      calendar: {id: 39, name: null, subscription: false, owner: {id: 10, providerId: 'space', space: {displayName: 'Chemistry'}}},
    };

    it('offers no answer and no actions menu, where an invited event offers both', () => {
      const toolbar = event => {
        const context = {event, $t, $root: {isMobile: false}};
        return {canEdit: AgendaEventDetailsToolbar.computed.canEdit.call(context)};
      };
      const details = event => ({isAttendee: AgendaEventDetails.computed.isAttendee.call({event})});

      expect(toolbar(feedEvent).canEdit).toBeFalsy();
      expect(details(feedEvent).isAttendee).toBeFalsy();
      expect(toolbar(invitedEvent).canEdit).toBe(true);
      expect(details(invitedEvent).isAttendee).toBe(true);
    });

    it('draws the answer buttons and the menu only for an attendee and an editor', () => {
      const toolbar = fs.readFileSync(path.resolve(__dirname,
                                                   '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventDetailsToolbar.vue'),
                                      'utf8');
      const grid = fs.readFileSync(path.resolve(__dirname,
                                                '../../main/webapp/vue-app/agenda-common/components/calendar-body/AgendaCalendar.vue'),
                                   'utf8');

      expect(toolbar).toMatch(/<template v-if="!isTentativeEvent && isAttendee && !\$root\.isMobile">/);
      expect(toolbar).toMatch(/<v-menu\s+v-if="canEdit"/);
      expect(toolbar.match(/<v-list-item v-if="canEdit"/g).length).toBeGreaterThanOrEqual(2);
      // the grid marks such an event read-only and refuses to drag or resize it
      expect(grid).toContain('const editModeStyle = this.canEdit(event) && \'editable-event\' || \'readonly-event\';');
      expect(grid).toMatch(/if \(!dragEvent \|\| !dragEvent\.acl \|\| !dragEvent\.acl\.canEdit\) \{/);
    });

  });

  it('has an English sentence for every key the space\'s screens use', () => {
    const sources = [
      'vue-app/agenda-common/components/filter/AgendaSpaceSubscriptionsDrawer.vue',
      'vue-app/agenda-common/components/filter/AgendaCalendarSubscriptionDrawer.vue',
      'vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue',
      'vue-app/agenda-space-administration/components/AgendaSpaceAdministration.vue',
      'vue-app/agenda-common/components/event/view/AgendaEventsDetailsBody.vue',
    ].map(file => fs.readFileSync(path.resolve(__dirname, '../../main/webapp', file), 'utf8')).join('\n');
    const keys = new Set((sources.match(/'agenda\.(calendarSubscription|space\.settings)\.[A-Za-z.]+'/g) || []).map(key => key.slice(1, -1)));

    expect(keys.size).toBeGreaterThan(15);
    for (const key of keys) {
      expect(bundle).toMatch(new RegExp(`^${key.replace(/\./g, '\\.')}=.+$`, 'm'));
    }
  });

});
