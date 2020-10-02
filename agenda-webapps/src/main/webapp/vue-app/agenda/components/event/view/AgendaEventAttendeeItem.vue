<template>
  <v-list-item
    :id="`attendee${attendee.id}`"
    class="d-flex attendee">
    <v-list-item-content>
      <a :href="attendeeProfileLink" class="mr-5 my-auto text-truncate">
        <v-list-item-avatar>
          <v-avatar size="32">
            <v-img :src="attendeeProfileAvatarUrl" />
          </v-avatar>
        </v-list-item-avatar>
        {{ attendeeProfileDisplayName }}
      </a>
    </v-list-item-content>
    <v-list-item-action :title="responseIconTooltip">
      <span :class="responseIconResponse"></span>
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    attendee: {
      type: Object,
      default: () => ({})
    },
  },
  computed: {
    responseIconResponse() {
      return this.attendee && this.attendee.response && `attendee-response attendee-response-${this.attendee.response.toLowerCase()}`;
    },
    responseIconTooltip() {
      return this.attendee && this.attendee.response && this.$t(`agenda.${this.attendee.response.toLowerCase()}`);
    },
    attendeeProfileLink() {
      if (this.attendee.identity.providerId === 'organization') {
        return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.attendee.identity.remoteId}`;
      } else if (this.attendee.identity.providerId === 'space') {
        return `${eXo.env.portal.context}/g/:spaces:${this.attendee.identity.remoteId}/`;
      }
      return '';
    },
    attendeeProfileAvatarUrl() {
      return this.attendee.identity.space ? this.attendee.identity.space.avatarUrl : this.attendee.identity.profile ? this.attendee.identity.profile.avatar : '';

    },
    attendeeProfileDisplayName() {
      return this.attendee.identity.space ? this.attendee.identity.space.displayName : this.attendee.identity.profile ? this.attendee.identity.profile.fullname : '';
    },
    spaceId() {
      return this.attendee && this.attendee.identity && this.attendee.identity.space && this.attendee.identity.space.id;
    },
    spacePrettyName() {
      return this.attendee && this.attendee.identity && this.attendee.identity.space && this.attendee.identity.space.prettyName;
    },
  },
  mounted() {
    this.initPopup();
  },
  methods: {
    initPopup() {
      const attendeeId = `attendee${this.attendee.id}`;
      const restUrl = `//${window.location.host}${eXo.env.portal.context}/${eXo.env.portal.rest}/social/people/getPeopleInfo/{0}.json`;
      const labels = {
        youHaveSentAnInvitation: this.$t('message.label'),
        StatusTitle: this.$t('Loading.label'),
        Connect: this.$t('Connect.label'),
        Confirm: this.$t('Confirm.label'),
        CancelRequest: this.$t('CancelRequest.label'),
        RemoveConnection: this.$t('RemoveConnection.label'),
        Ignore: this.$t('Ignore.label')
      };
      if (this.attendee.identity.space) {
        const thiz = this;
        $(`#${attendeeId}`).find('a').each(function (idx, el) {
          $(el).spacePopup({
            userName: eXo.env.portal.userName,
            spaceID: thiz.spaceId,
            restURL: '/portal/rest/v1/social/spaces/{0}',
            membersRestURL: '/portal/rest/v1/social/spaces/{0}/users?returnSize=true',
            managerRestUrl: '/portal/rest/v1/social/spaces/{0}/users?role=manager&returnSize=true',
            membershipRestUrl: '/portal/rest/v1/social/spacesMemberships?space={0}&returnSize=true',
            defaultAvatarUrl: `/portal/rest/v1/social/spaces/${thiz.spacePrettyName}/avatar`,
            deleteMembershipRestUrl: '/portal/rest/v1/social/spacesMemberships/{0}:{1}:{2}',
            labels: this.labels,
            content: false,
            keepAlive: true,
            defaultPosition: 'left',
            maxWidth: '240px'
          });
        });
      } else {
        $(`#${attendeeId}`).find('a').each(function (idx, el) {
          $(el).userPopup({
            restURL: restUrl,
            labels: labels,
            content: false,
            defaultPosition: 'left',
            keepAlive: true,
            maxWidth: '240px'
          });
        });
      }
    }
  }
};
</script>