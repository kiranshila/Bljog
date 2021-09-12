import skrf as rf
from matplotlib import pyplot as plt
from matplotlib import style
import matplotlib as mpl
from skrf.plotting import save_all_figs, legend_off
from pylab import *
import os
from cycler import cycler
import re

def natural_sort(l):
    convert = lambda text: int(text) if text.isdigit() else text.lower()
    alphanum_key = lambda key: [ convert(c) for c in re.split('([0-9]+)', key) ]
    return sorted(l, key = alphanum_key)

rf.stylely()

Title = 'Diramics 4F250'
S_Param_Directory = '4F250_13K/'

mpl.rcParams['axes.prop_cycle'] = cycler(color=['blue', 'green', 'red', 'cyan', 'magenta', 'yellow', 'black', 'purple', 'pink',
                                    'brown', 'orange', 'teal', 'coral', 'lightblue', 'lime', 'lavender', 'turquoise',
                                    'darkgreen', 'tan', 'salmon', 'gold'])
S_Params = [] # Empty list for all s parameters

for file in natural_sort(os.listdir(S_Param_Directory)):
    if file.endswith('.s2p'):
        S_Params.append(rf.Network(os.path.join(S_Param_Directory,file)))

# Fix ferquencies
for S_Param in S_Params:
    S_Param.frequency.unit = 'GHz'


f,ax = plt.subplots(2,2,figsize=(10,10))


plt.subplot(2,2,1)
for S_Param in S_Params:
    S_Param.plot_s_smith(m=0,n=0)
plt.title('S11')
legend_off()

plt.subplot(2,2,4)
for S_Param in S_Params:
    S_Param.plot_s_smith(m=1,n=1)
plt.title('S22')
legend_off()

plt.subplot(2,2,2)
for S_Param in S_Params:
    S_Param.plot_s_db(m=1,n=0)
plt.title('S21')
#plt.ylim(10,20)
legend_off()

plt.subplot(2,2,3)
for S_Param in S_Params:
    S_Param.plot_s_db(m=0,n=1,label=S_Param.name)
plt.title('S12')
legend_off()

plt.subplots_adjust(hspace=0.3)

plt.suptitle(Title,size=20)

#plt.figlegend(loc='center right',ncol=5,labelspacing=0.)
legend(loc='lower right', ncol=1)

save_all_figs('Figs/',format=['pdf'])

