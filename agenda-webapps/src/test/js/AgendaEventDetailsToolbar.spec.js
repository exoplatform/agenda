import {shallowMount} from '@vue/test-utils';

import AgendaEventDetailsToolbar
  from '../../main/webapp/vue-app/agenda-common/components/event/view/AgendaEventDetailsToolbar.vue';

/*
 * Stubs that keep the props they receive readable, so a test can pin *which*
 * component the header handed the calendar owner to — the whole point of
 * EXO-89750, where a user profile was handed to the space avatar.
 */
const ExoSpaceAvatarStub = {
  name: 'exo-space-avatar',
  props: ['space'],
  template: '<div class="space-avatar-stub"></div>',
};

const ExoUserAvatarStub = {
  name: 'exo-user-avatar',
  props: ['identity'],
  template: '<div class="user-avatar-stub"></div>',
};

const AgendaConnectorAvatarStub = {
  name: 'agenda-connector-avatar',
  props: ['connector'],
  template: '<div class="connector-avatar-stub"></div>',
};

const passthrough = tag => ({
  template: `<${tag}><slot></slot></${tag}>`,
});

/**
 * A calendar owner identity as the Agenda REST layer builds it for a space:
 * IdentityEntity.providerId 'space' and the space entity in `space`.
 */
function spaceOwner() {
  return {
    id: '42',
    providerId: 'space',
    remoteId: 'marketing',
    space: {
      id: '42',
      displayName: 'Marketing',
      prettyName: 'marketing',
      avatarUrl: '/portal/rest/v1/social/spaces/marketing/avatar',
    },
  };
}

/**
 * A calendar owner identity as the Agenda REST layer builds it for a user:
 * IdentityEntity.providerId 'organization' and a social ProfileEntity in
 * `profile` — note it carries `fullname` and `avatar`, never `displayName`,
 * `avatarUrl` or `prettyName`.
 */
function userOwner() {
  return {
    id: '7',
    providerId: 'organization',
    remoteId: 'jsmith',
    profile: {
      id: '7',
      username: 'jsmith',
      fullname: 'John Smith',
      avatar: '/portal/rest/v1/social/users/7/avatar',
    },
  };
}

/*
 * $t rendered as the key followed by the values it was given, so a spec can
 * tell "named the account" from "named nothing" — which the bare key alone
 * cannot express, both being the same string.
 */
function translate(key, params) {
  if (!params) {
    return key;
  }
  return `${key}|${Object.keys(params).sort().map(index => params[index]).join('|')}`;
}

function mountToolbar(event, options) {
  const opts = options || {};
  return shallowMount(AgendaEventDetailsToolbar, {
    propsData: {
      event,
      connectedConnector: opts.connectedConnector || {name: 'caldav'},
      isAttendee: false,
    },
    mocks: {
      $t: translate,
      $remoteEventConnector: {
        remoteCalendarName: opts.remoteCalendarName || (() => Promise.resolve(null)),
      },
    },
    stubs: {
      'exo-space-avatar': ExoSpaceAvatarStub,
      'exo-user-avatar': ExoUserAvatarStub,
      'agenda-connector-avatar': AgendaConnectorAvatarStub,
      'agenda-event-attendee-buttons': true,
      'extension-registry-components': true,
      'v-row': passthrough('div'),
      'v-col': passthrough('div'),
      'v-menu': passthrough('div'),
      'v-list': passthrough('div'),
      'v-list-item': passthrough('div'),
      'v-list-item-title': passthrough('div'),
      'v-btn': passthrough('button'),
      'v-icon': passthrough('i'),
    },
  });
}

const ownerLabel = wrapper => wrapper.find('.calendar-owner-link');
const remoteLabel = wrapper => wrapper.find('.remote-calendar-label');

describe('AgendaEventDetailsToolbar owner header', () => {

  describe('an event on a space calendar', () => {
    const wrapper = mountToolbar({
      summary: 'Sprint review',
      calendar: {id: 12, name: null, owner: spaceOwner()},
    });

    it('renders the space avatar, which draws the space name itself', () => {
      const spaceAvatar = wrapper.findComponent(ExoSpaceAvatarStub);
      expect(spaceAvatar.exists()).toBe(true);
      expect(spaceAvatar.props('space')).toEqual(spaceOwner().space);
    });

    it('renders no user avatar and no separate label', () => {
      expect(wrapper.findComponent(ExoUserAvatarStub).exists()).toBe(false);
      expect(ownerLabel(wrapper).exists()).toBe(false);
    });
  });

  describe('an event on a personal calendar materialised from a connected account', () => {
    const wrapper = mountToolbar({
      summary: 'Dentist',
      calendar: {id: 68, name: 'CAL3', owner: userOwner()},
    });

    it('reads the calendar own name, not the owner name', () => {
      expect(ownerLabel(wrapper).text()).toBe('CAL3');
    });

    it('hands the user profile to the user avatar', () => {
      const userAvatar = wrapper.findComponent(ExoUserAvatarStub);
      expect(userAvatar.exists()).toBe(true);
      expect(userAvatar.props('identity')).toEqual(userOwner().profile);
    });

    it('never hands a user profile to the space avatar — the EXO-89750 defect', () => {
      expect(wrapper.findComponent(ExoSpaceAvatarStub).exists()).toBe(false);
    });

    it('links the label to the owner profile page', () => {
      expect(ownerLabel(wrapper).attributes('href')).toBe('/portal/dw/profile/jsmith');
    });
  });

  describe('an event on the unnamed default personal calendar', () => {
    const wrapper = mountToolbar({
      summary: 'Lunch',
      calendar: {id: 3, name: null, owner: userOwner()},
    });

    it('falls back to the owner display name', () => {
      expect(ownerLabel(wrapper).text()).toBe('John Smith');
    });

    it('still uses the user avatar, not the space one', () => {
      expect(wrapper.findComponent(ExoUserAvatarStub).exists()).toBe(true);
      expect(wrapper.findComponent(ExoSpaceAvatarStub).exists()).toBe(false);
    });
  });

  describe('an event fetched live from a connected account', () => {
    const wrapper = mountToolbar({
      summary: 'Standup',
      type: 'remoteEvent',
      connector: {name: 'caldav'},
    });

    it('keeps reading as the connected account', () => {
      expect(wrapper.findComponent(AgendaConnectorAvatarStub).exists()).toBe(true);
      expect(wrapper.findComponent(AgendaConnectorAvatarStub).props('connector')).toEqual({name: 'caldav'});
    });

    it('renders neither the space nor the user avatar', () => {
      expect(wrapper.findComponent(ExoSpaceAvatarStub).exists()).toBe(false);
      expect(wrapper.findComponent(ExoUserAvatarStub).exists()).toBe(false);
    });
  });

  /*
   * EXO-89825. A live connector read holds no eXo calendar — only the href of
   * the collection it lives in — and the header used to label every one of
   * them "Personal Calendar", a name no calendar of the user actually has.
   * A connected account holds several collections, so that label identified
   * nothing and made a stray event impossible to place.
   */
  describe('the calendar of an event read live from a connected account', () => {

    const remoteEvent = () => ({
      summary: 'test',
      type: 'remoteEvent',
      calendarId: '/dav/cal/alice@stalwart.local/default',
      connector: {name: 'caldav', user: 'alice@stalwart.local'},
    });

    it('names the collection the account holds the event in', async () => {
      const wrapper = mountToolbar(remoteEvent(), {
        remoteCalendarName: () => Promise.resolve('MYCAL2'),
      });
      await wrapper.vm.$nextTick();
      await wrapper.vm.$nextTick();
      expect(remoteLabel(wrapper).text()).toBe('MYCAL2');
    });

    it('never labels it "Personal Calendar", which names no calendar the user has', async () => {
      const wrapper = mountToolbar(remoteEvent(), {
        remoteCalendarName: () => Promise.resolve('MYCAL2'),
      });
      await wrapper.vm.$nextTick();
      await wrapper.vm.$nextTick();
      expect(wrapper.text()).not.toContain('agenda.personalCalendar');
    });

    it('asks the account the event came from, for the collection it came from', () => {
      const asked = [];
      const connector = {name: 'caldav', user: 'alice@stalwart.local'};
      mountToolbar({...remoteEvent(), connector}, {
        connectedConnector: {name: 'other-account', user: 'bob@stalwart.local'},
        remoteCalendarName: (usedConnector, href) => {
          asked.push([usedConnector, href]);
          return Promise.resolve('MYCAL2');
        },
      });
      expect(asked).toEqual([[connector, '/dav/cal/alice@stalwart.local/default']]);
    });

    it('says whose account the collection belongs to when the account cannot name it', async () => {
      const wrapper = mountToolbar(remoteEvent(), {
        remoteCalendarName: () => Promise.resolve(null),
      });
      await wrapper.vm.$nextTick();
      await wrapper.vm.$nextTick();
      expect(remoteLabel(wrapper).text()).toBe('agenda.remoteEvent.calendarOfAccount|alice@stalwart.local');
    });

    it('invents no name when neither the collection nor the account is known', async () => {
      const wrapper = mountToolbar({
        summary: 'test',
        type: 'remoteEvent',
        calendarId: '/dav/cal/somewhere/default',
        connector: {name: 'caldav'},
      }, {
        connectedConnector: {name: 'caldav'},
        remoteCalendarName: () => Promise.resolve(null),
      });
      await wrapper.vm.$nextTick();
      await wrapper.vm.$nextTick();
      expect(remoteLabel(wrapper).text()).toBe('agenda.remoteEvent.unnamedCalendar');
    });

    it('puts the collection href in the hover text, which is what tells two collections apart', () => {
      const wrapper = mountToolbar(remoteEvent());
      expect(remoteLabel(wrapper).attributes('title'))
        .toBe('agenda.remoteEvent.calendarLocation|/dav/cal/alice@stalwart.local/default');
    });

    it('drops a name that resolved after the dialog moved to another event', async () => {
      // One resolver per collection, so the test settles the request the
      // dialog has already moved away from and not the current one.
      const settle = {};
      const wrapper = mountToolbar(remoteEvent(), {
        remoteCalendarName: (connector, href) => new Promise(resolve => settle[href] = resolve),
      });
      wrapper.setProps({event: {
        summary: 'other',
        type: 'remoteEvent',
        calendarId: '/dav/cal/alice@stalwart.local/other',
        connector: {name: 'caldav', user: 'alice@stalwart.local'},
      }});
      await wrapper.vm.$nextTick();
      settle['/dav/cal/alice@stalwart.local/default']('MYCAL2');
      await wrapper.vm.$nextTick();
      await wrapper.vm.$nextTick();
      expect(remoteLabel(wrapper).text()).not.toBe('MYCAL2');
    });

    it('asks nothing at all for a stored event, which names its calendar already', () => {
      let asked = 0;
      mountToolbar({
        summary: 'Dentist',
        calendar: {id: 68, name: 'CAL3', owner: userOwner()},
      }, {
        remoteCalendarName: () => {
          asked++;
          return Promise.resolve(null);
        },
      });
      expect(asked).toBe(0);
    });
  });

  describe('an event whose calendar owner could not be resolved', () => {
    const wrapper = mountToolbar({summary: 'Orphan', calendar: null});

    it('renders no owner at all rather than an empty avatar box', () => {
      expect(wrapper.findComponent(ExoSpaceAvatarStub).exists()).toBe(false);
      expect(wrapper.findComponent(ExoUserAvatarStub).exists()).toBe(false);
      expect(ownerLabel(wrapper).exists()).toBe(false);
    });
  });
});
