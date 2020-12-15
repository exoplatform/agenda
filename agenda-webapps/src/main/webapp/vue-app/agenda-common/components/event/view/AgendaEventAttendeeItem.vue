<template>
  <exo-user-avatar
    v-if="isAttendeeUser"
    :title="attendeeProfileDisplayName"
    :username="attendeeRemoteId"
    :fullname="attendeeProfileDisplayName"
    :url="attendeeProfileLink"
    :size="32"
    :avatar-url="attendeeProfileAvatarUrl"
    :labels="labels"
    avatar-class="border-color"
    class="attendee">
    <template slot="actions">
      <div class="my-auto flex-grow-1 text-right">
        <v-icon
          v-if="isCreator"
          :title="$t('agenda.eventCreator')"
          :size="16"
          class="mb-2">
          fa-crown
        </v-icon>
        <span :class="responseIconResponse"></span>
      </div>
    </template>
  </exo-user-avatar>
  <div v-else-if="isAttendeeSpace" class="flex-nowrap d-flex flex-shrink-0 align-center attendee">
    <exo-space-avatar
      :space="attendeeSpace"
      :size="32"
      :labels="labels"
      class="flex-grow-1" />
    <span :class="responseIconResponse" class="my-auto text-right"></span>
  </div>
</template>
<script>
export default {
  props: {
    attendee: {
      type: Object,
      default: () => ({})
    },
    creator: {
      type: Object,
      default: () => null
    },
  },
  computed: {
    isCreator() {
      return this.creator && this.attendee.identity && Number(this.creator.id) === Number(this.attendee.identity.id);
    },
    responseIconResponse() {
      return this.attendee && this.attendee.response && `attendee-response attendee-response-${this.attendee.response.toLowerCase()}`;
    },
    responseIconTooltip() {
      return this.attendee && this.attendee.response && this.$t(`agenda.${this.attendee.response.toLowerCase()}`);
    },
    isAttendeeUser() {
      return this.attendee && this.attendee.identity && this.attendee.identity.providerId === 'organization';
    },
    isAttendeeSpace() {
      return this.attendee && this.attendee.identity && this.attendee.identity.providerId === 'space';
    },
    attendeeSpace() {
      if (!this.isAttendeeSpace) {
        return null;
      }
      return this.attendee.identity.space;
    },
    attendeeProfileLink() {
      if (this.isAttendeeUser) {
        return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/profile/${this.attendee.identity.remoteId}`;
      } else if (this.isAttendeeSpace) {
        let groupId = this.attendee.identity.space.groupId;
        if (groupId) {
          groupId = groupId.replace(/\//g, ':');
          return `${eXo.env.portal.context}/g/${groupId}/`;
        }
      }
      return '';
    },
    attendeeRemoteId() {
      return this.attendee && this.attendee.identity && this.attendee.identity.remoteId;
    },
    attendeeProfileAvatarUrl() {
      return this.attendee.identity.space ? this.attendee.identity.space.avatarUrl : this.attendee.identity.profile ? this.attendee.identity.profile.avatar : '';
    },
    attendeeProfileDisplayName() {
      return this.attendee.identity.space ? this.attendee.identity.space.displayName : this.attendee.identity.profile ? this.attendee.identity.profile.fullname : '';
    },
    labels() {
      return {
        CancelRequest: this.$t('profile.CancelRequest'),
        Confirm: this.$t('profile.Confirm'),
        Connect: this.$t('profile.Connect'),
        Ignore: this.$t('profile.Ignore'),
        RemoveConnection: this.$t('profile.RemoveConnection'),
        StatusTitle: this.$t('profile.StatusTitle'),
        join: this.$t('space.join'),
        leave: this.$t('space.leave'),
        members: this.$t('space.members'),
      };
    },
  }
};
</script>