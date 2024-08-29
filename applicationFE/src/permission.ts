import router from "./router/index";

router.beforeEach(async (to, from, next) => {
  console.log('## to ### : ', to)
  console.log('## from ### : ', from)

  next();
});