<template>
  <input
    v-model="query"
    type="text"
    class="agenda-calendar-filter-input input-large ignore-vuetify-classes"
    :placeholder="$t('agenda.SearchCalendarPlaceholder')">
</template>

<script>
export default {
  props: {
    value: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    startSearchAfterInMilliseconds: 600,
    endTypingKeywordTimeout: 50,
    startTypingKeywordTimeout: 0,
    query: null,
    loading: false,
  }),
  watch: {
    query() {
      if (!this.query) {
        this.loading = false;
        this.$emit('input', '');
        return;
      }
      this.startTypingKeywordTimeout = Date.now();
      if (!this.loading) {
        this.loading = true;
        this.waitForEndTyping();
      }
    },
  },
  methods: {
    waitForEndTyping() {
      window.setTimeout(() => {
        if (Date.now() - this.startTypingKeywordTimeout > this.startSearchAfterInMilliseconds) {
          this.loading = false;
          this.$emit('input', this.query);
          return;
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
  },
};
</script>