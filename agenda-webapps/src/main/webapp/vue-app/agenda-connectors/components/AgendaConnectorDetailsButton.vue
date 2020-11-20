<template>
  <div class="">
    <div v-if="connectedAccountName">
      <v-avatar tile size="24">
        <img
          :alt="connectedAccount.name"
          :src="connectedAccountIconSource">
      </v-avatar>
      <a
        class="mx-2"
        @click="openPersonalCalendarDrawer">
        {{ connectedAccountName }}
      </a>
    </div>
    <div
      v-else
      class="connectButton"
      @click="openPersonalCalendarDrawer">
      <slot
        name="connectButton">
      </slot>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => []
    },
    connectedAccount: {
      type: Array,
      default: () => []
    }
  },
  computed: {
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    },
  }
};
</script>