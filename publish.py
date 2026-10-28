"""Ship a new game build to phones: copies the mobile build into the app and the update feed, bumps the version.
   python3 publish.py "What changed"   (then commit and push)"""
import json,shutil,sys,os
src=sys.argv[2] if len(sys.argv)>2 else '../last-convoy-mobile.html'
vf='updates/version.json';v=json.load(open(vf)) if os.path.exists(vf) else {'version':0,'apkVersion':0}
v['version']+=1;v['notes']=sys.argv[1] if len(sys.argv)>1 else ''
shutil.copy(src,'updates/game.html');shutil.copy(src,'app/src/main/assets/game.html')
open('app/src/main/assets/version.txt','w').write(str(v['version']))
json.dump(v,open(vf,'w'),indent=1);print('game version',v['version'])
