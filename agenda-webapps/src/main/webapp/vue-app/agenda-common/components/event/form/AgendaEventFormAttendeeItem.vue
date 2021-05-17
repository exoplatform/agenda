<template>
  <v-chip
    :close="canRemoveAttendee"
    class="identitySuggesterItem me-4 mt-4"
    @click:close="$emit('remove-attendee', attendee)">
    <v-avatar left>
      <v-img :src="avatarUrl" />
    </v-avatar>
    <span class="text-truncate">
      {{ displayName }}
    </span>
  </v-chip>
</template>

<script>
export default {
  props: {
    attendee: {
      type: Object,
      default: () => ({}),
    },
    creator: {
      type: Object,
      default: () => ({}),
    },
  },
  computed: {
    canRemoveAttendee() {
      if (this.creator && this.creator.id) {
        return Number(this.attendee.identity.id) !== Number(this.creator.id);
      } else {
        return Number(this.attendee.identity.id) !== Number(eXo.env.portal.userIdentityId);
      }
    },
    avatarUrl() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      return profile && (profile.avatarUrl || profile.avatar);
    },
    displayName() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      const fullName = profile && (profile.displayName || profile.fullname || profile.fullName);
      return this.isExternal ? `${fullName} (${this.$t('profile.External')})` : fullName;
    },
    isExternal() {
      const profile = this.attendee.identity && this.attendee.identity.profile ;
      return profile && (profile.dataEntity && profile.dataEntity.external === 'true' || profile.external);
    },
  },
};
</script>