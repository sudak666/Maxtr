document.querySelectorAll('[data-faq-toggle]').forEach(function(q){
  q.addEventListener('click', function(){
    var item = q.closest('.faq-item');
    var wasOpen = item.classList.contains('open');
    document.querySelectorAll('.faq-item.open').forEach(function(o){ o.classList.remove('open'); });
    if(!wasOpen) item.classList.add('open');
  });
});
document.getElementById('theme-toggle').addEventListener('click', function(){
  var isLight = document.documentElement.getAttribute('data-theme') === 'light';
  var next = isLight ? 'dark' : 'light';
  if(next === 'light') document.documentElement.setAttribute('data-theme','light');
  else document.documentElement.removeAttribute('data-theme');
  try{ localStorage.setItem('xamssTheme', next); }catch(e){}
});
