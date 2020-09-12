<template>
  <v-chip
    close
    class="identitySuggesterItem mr-4 mt-4"
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
  },
  computed: {
    avatarUrl() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      return profile && (profile.avatarUrl || profile.avatar);
    },
    displayName() {
      const profile = this.attendee.identity && (this.attendee.identity.profile || this.attendee.identity.space);
      return profile && (profile.displayName || profile.fullname || profile.fullName);
    },
  },
};
</script>