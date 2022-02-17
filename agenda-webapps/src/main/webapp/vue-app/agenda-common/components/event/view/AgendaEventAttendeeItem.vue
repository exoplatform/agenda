<template>
  <exo-user
    v-if="isAttendeeUser"
    :identity="attendeeIdentity"
    :url="attendeeProfileLink"
    :size="32"
    avatar-class="border-color"
    class="attendee"
    popover
    link-style>
    <template slot="actions">
      <div
        :title="responseIconTooltip"
        class="my-auto flex-grow-1 text-right">
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
  </exo-user>
  <div v-else-if="isAttendeeSpace" class="attendee">
    <exo-space-avatar
      :space="attendeeSpace"
      :size="32"
      popover />
    <span
      :title="responseIconTooltip"
      :class="responseIconResponse"
      class="my-auto text-right"></span>
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
    attendeeIdentity() {
      return this.attendee && this.attendee.identity && this.attendee.identity.profile;
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
        External: this.$t('agenda.label.external'),
        Disabled: this.$t('agenda.label.disabled'),
        members: this.$t('space.members'),
      };
    },
  }
};
</script>
