<template>
  <span ref="el" class="animated-number">{{ formatted }}</span>
</template>

<script>
// Counts up to `to` shortly after mount, and again from the current value
// whenever `to` changes (the manifest often arrives after the first run).
export default {
  name: "AnimatedNumber",
  props: {
    to: { type: Number, required: true },
    duration: { type: Number, default: 1200 },
  },
  data() {
    return { display: 0, reduce: false };
  },
  computed: {
    formatted() {
      return this.display.toLocaleString("zh-CN");
    },
  },
  watch: {
    to(value) {
      clearTimeout(this.timer);
      if (this.reduce) this.display = value;
      else this.run(this.display);
    },
  },
  mounted() {
    this.reduce =
      window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (this.reduce) {
      this.display = this.to;
      return;
    }
    // Hero stats are above the fold — animate shortly after mount so the value
    // is always correct even where IntersectionObserver wouldn't fire.
    this.timer = setTimeout(() => this.run(0), 250);
  },
  beforeUnmount() {
    clearTimeout(this.timer);
    cancelAnimationFrame(this.frame);
  },
  methods: {
    run(from) {
      cancelAnimationFrame(this.frame);
      const start = performance.now();
      const tick = (now) => {
        const p = Math.min((now - start) / this.duration, 1);
        const eased = 1 - Math.pow(1 - p, 3); // easeOutCubic
        this.display = Math.round(from + eased * (this.to - from));
        if (p < 1) this.frame = requestAnimationFrame(tick);
        else this.display = this.to;
      };
      this.frame = requestAnimationFrame(tick);
    },
  },
};
</script>

<style scoped>
.animated-number { font-variant-numeric: tabular-nums; }
</style>
