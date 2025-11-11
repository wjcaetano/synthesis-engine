import pandas as pd

def simple_stats():
    df = pd.DataFrame({'a': [1,2,3], 'b': [10,20,30]})
    df['sum'] = df['a'] + df['b']
    return { 'mean_sum': df['sum'].mean(), 'max_sum': df['sum'].max() }

asf = 19
print("Hello from Python via python file")
simple_stats()